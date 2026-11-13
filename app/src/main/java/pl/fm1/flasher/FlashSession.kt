package pl.fm1.flasher

import java.io.Closeable
import java.util.BitSet

data class Endpoint(val id: Int, val usbPath: String)

/** A new MIDI id/name alone never authorizes changing the target of a write. */
class ReconnectGate(private val previous: Endpoint, before: List<Endpoint>) {
    private val oldIds = before.map { it.id }.toSet() + previous.id
    private val protectedPaths = before.filter { it.id != previous.id }.map { it.usbPath }.toSet()
    fun candidates(current: List<Endpoint>): List<Endpoint> {
        if (current.any { it.id == previous.id }) return emptyList()
        return current.filter { it.id !in oldIds && it.usbPath !in protectedPaths }
    }
}

interface MidiConnection : Closeable {
    val endpoint: Endpoint
    fun send(bytes: ByteArray)
    fun receive(timeoutMs: Long): ByteArray?
}

class FlashSession(
    private val reconnect: (Endpoint, Boolean) -> MidiConnection,
    private val report: (String, Int) -> Unit,
    private val pause: (Long) -> Unit = { Thread.sleep(it) },
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    fun identify(link: MidiConnection): FirmwareIdentity {
        repeat(3) {
            link.send(Protocol.identityQuery)
            val deadline = now() + 1200
            while (now() < deadline) {
                val message = link.receive((deadline - now()).coerceAtLeast(1)) ?: break
                Protocol.identity(message)?.let { return it }
            }
        }
        error("Urządzenie nie odpowiedziało poprawną tożsamością FM‑1.")
    }

    fun flash(image: FirmwarePackage, initial: MidiConnection, expectedCurrent: FirmwareIdentity): FirmwareIdentity {
        var link = initial
        val started = now()
        try {
            val current = identify(link)
            check(current.sameAs(expectedCurrent) && current.baseModel == image.identity.model) {
                "Tożsamość wybranego FM‑1 zmieniła się od potwierdzenia. Połącz go ponownie."
            }
            if (!current.isLoader) {
                report("Etap 1/2 · weryfikacja pakietu przez FM‑1", -1)
                serve(link, image, Protocol.VERIFIED, 15_000, started)
                pause(3000)
                val previous = link.endpoint
                link.close()
                link = reconnect(previous, true)
                check(identify(link).baseModel == image.identity.model) { "Wskazany program aktualizujący nie jest FM‑1." }
            }
            report("Etap 2/2 · zapis firmware. Nie odłączaj kabla.", 0)
            serve(link, image, Protocol.FINISHED, 180_000, started)
            pause(1500)
            val previous = link.endpoint
            link.close()
            link = reconnect(previous, false)
            val installed = identify(link)
            check(installed.sameAs(image.identity)) {
                "Po zapisie FM‑1 zgłasza $installed, oczekiwano ${image.identity}. Wynik niepotwierdzony."
            }
            report("Zakończono · FM‑1 zgłosił $installed. Tożsamość zgodna.", 100)
            return installed
        } finally {
            link.close()
        }
    }

    private fun serve(link: MidiConnection, image: FirmwarePackage, terminal: Long, idleMs: Long, started: Long) {
        link.send(Protocol.upgrade)
        pause(2000)
        var lastRequest = now()
        val covered = BitSet(image.logical.size)
        var requests = 0
        var lastPercent = -1
        while (now() - lastRequest < idleMs && now() - started < 15 * 60_000) {
            val message = link.receive(500) ?: continue
            val request = Protocol.request(message) ?: continue
            if (request.address == Protocol.VERIFIED || request.address == Protocol.FINISHED) {
                check(request.address == terminal && request.length == 8 && request.flashType == 0 && requests > 0) {
                    "FM‑1 przerwał aktualizację na nieoczekiwanym etapie. Nie potwierdzono zapisu."
                }
                link.send(Protocol.response(request, "success\u0000".toByteArray(Charsets.US_ASCII)))
                return
            }
            check(request.length in 1..Protocol.MAX_DATA && request.address <= image.logical.size &&
                request.address + request.length <= image.logical.size) { "FM‑1 zażądał danych spoza pakietu firmware." }
            val start = request.address.toInt()
            pause(10)
            link.send(Protocol.response(request, image.logical.copyOfRange(start, start + request.length)))
            requests++
            covered.set(start, start + request.length)
            val percent = (covered.cardinality() * 100L / image.logical.size).toInt().coerceAtMost(99)
            if (terminal == Protocol.FINISHED && percent != lastPercent) {
                lastPercent = percent
                report("Etap 2/2 · przekazano $percent% pakietu. Nie odłączaj kabla.", percent)
            }
            lastRequest = now()
        }
        error("Brak potwierdzenia FM‑1 w wyznaczonym czasie. Wynik aktualizacji niepotwierdzony.")
    }
}
