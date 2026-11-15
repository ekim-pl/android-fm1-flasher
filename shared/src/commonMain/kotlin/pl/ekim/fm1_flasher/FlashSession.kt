package pl.ekim.fm1_flasher


data class Endpoint(val id: Int, val usbPath: String, val generation: Long = 0)

/** Once a bystander disconnects, this transition can never regain trust. */
class ReconnectGate(private val previous: Endpoint, before: List<Endpoint>, usbBefore: Set<String>? = null, private val retiredUsbPaths: Set<String> = emptySet()) {
    private val siblings = before.filter { it != previous }.toSet()
    private val old = before.toSet() + previous
    private val usbPeers = usbBefore?.minus(previous.usbPath)?.minus(retiredUsbPaths)
    private var lost = false
    fun observeUsb(paths: Set<String>) { if (usbPeers != null && !paths.containsAll(usbPeers)) lost = true }
    fun removed(endpoint: Endpoint) { if (endpoint in siblings) lost = true }
    fun removedPath(path: String) {
        // Android may deliver the previous stage's detach after the new MIDI port is bound.
        if (path != previous.usbPath && path !in retiredUsbPaths) lost = true
    }
    fun observe(current: List<Endpoint>) { if (!current.containsAll(siblings)) lost = true }
    fun valid(current: List<Endpoint>): Boolean { observe(current); return !lost }
    fun candidates(current: List<Endpoint>, usbPaths: Set<String>? = null): List<Endpoint> {
        if (usbPaths != null) observeUsb(usbPaths)
        if (!valid(current) || previous in current) return emptyList()
        val candidates = current.filter { it !in old && it.usbPath !in siblings.map { other -> other.usbPath } }
        return candidates.takeIf { it.size == 1 && (usbPeers == null || usbPaths == null ||
            (usbPaths - usbPeers).singleOrNull() == it.single().usbPath) }.orEmpty()
    }
    fun automaticCandidate(current: List<Endpoint>, usbPaths: Set<String>): Endpoint? =
        if (siblings.isEmpty()) candidates(current, usbPaths).singleOrNull()?.takeIf { usbPaths.singleOrNull() == it.usbPath } else null
}

interface MidiConnection : AutoCloseable {
    val endpoint: Endpoint
    @Throws(Exception::class) fun send(bytes: ByteArray)
    @Throws(Exception::class) fun receive(timeoutMs: Long): ByteArray?
    @Throws(Exception::class) override fun close()
}

class FlashSession(
    private val reconnect: (Endpoint, Boolean) -> MidiConnection,
    private val report: (AppText, Int) -> Unit,
    private val pause: (Long) -> Unit = { platformPause(it) },
    private val now: () -> Long = { monotonicMillis() },
    private val validate: (Endpoint) -> Unit = {},
) {
    companion object { const val MAX_SESSION_MS = 15 * 60_000L }
    private var sessionDeadline = Long.MAX_VALUE
    private fun checkTime() { check(now() < sessionDeadline) { failed(msg("flash_timeout")) } }

    fun identify(link: MidiConnection): FirmwareIdentity {
        repeat(3) {
            checkTime()
            link.send(Protocol.identityQuery)
            val deadline = minOf(now() + 1200, sessionDeadline)
            while (now() < deadline) {
                val message = link.receive((deadline - now()).coerceAtLeast(1)) ?: break
                Protocol.identity(message)?.let { return it }
            }
        }
        failed(msg("flash_identity_missing"))
    }

    fun flash(image: FirmwarePackage, initial: MidiConnection, expectedCurrent: FirmwareIdentity): FirmwareIdentity {
        var link = initial
        val started = now()
        sessionDeadline = started + MAX_SESSION_MS
        try {
            val current = identify(link)
            check(current.sameAs(expectedCurrent) && current.baseModel == image.identity.model) {
                failed(msg("flash_identity_changed"))
            }
            if (!current.isLoader) {
                report(msg("flash_stage_verify"), -1)
                serve(link, image, Protocol.VERIFIED, 15_000, started)
                pause(3000)
                val previous = link.endpoint
                link.close()
                checkTime()
                link = reconnect(previous, true)
                checkTime()
                val loader = identify(link)
                check(loader.isLoader && loader.baseModel == image.identity.model) { failed(msg("loader_not_fm1")) }
            }
            report(msg("flash_stage_write"), 0)
            serve(link, image, Protocol.FINISHED, 180_000, started)
            pause(1500)
            val previous = link.endpoint
            link.close()
            checkTime()
            link = reconnect(previous, false)
            checkTime()
            val installed = identify(link)
            check(installed.sameAs(image.identity)) {
                failed(msg("flash_identity_mismatch", installed, image.identity))
            }
            validate(link.endpoint)
            report(msg("flash_completed", installed), 100)
            return installed
        } finally {
            link.close()
        }
    }

    private fun serve(link: MidiConnection, image: FirmwarePackage, terminal: Long, idleMs: Long, started: Long) {
        checkTime()
        validate(link.endpoint)
        link.send(Protocol.upgrade)
        pause(2000)
        var lastRequest = now()
        val covered = if (terminal == Protocol.FINISHED) BooleanArray(image.logical.size) else null
        var coveredBytes = 0
        var requests = 0
        var lastPercent = -1
        while (now() - lastRequest < idleMs && now() - started < MAX_SESSION_MS) {
            val message = link.receive(500) ?: continue
            val request = Protocol.request(message) ?: continue
            if (request.address == Protocol.VERIFIED || request.address == Protocol.FINISHED) {
                check(request.address == terminal && request.length == 8 && request.flashType == 0 && requests > 0) {
                    failed(msg("flash_stage_unexpected"))
                }
                link.send(Protocol.response(request, "success\u0000".encodeToByteArray()))
                return
            }
            check(request.length in 1..Protocol.MAX_DATA && request.address <= image.logical.size &&
                request.address + request.length <= image.logical.size) { failed(msg("flash_out_of_bounds")) }
            val start = request.address.toInt()
            pause(10)
            validate(link.endpoint)
            link.send(Protocol.response(request, image.logical.copyOfRange(start, start + request.length)))
            requests++
            if (covered != null) {
                val end = start + request.length
                for (index in start until end) {
                    if (!covered[index]) { covered[index] = true; coveredBytes++ }
                }
                val percent = (coveredBytes * 100L / image.logical.size).toInt().coerceAtMost(99)
                if (percent != lastPercent) {
                    lastPercent = percent
                    report(msg("flash_progress", percent), percent)
                }
            }
            lastRequest = now()
        }
        failed(msg("flash_timeout"))
    }
}
