package pl.ekim.fm1_flasher

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.Proxy
import java.net.URI
import java.util.concurrent.TimeUnit

/** DNS results are validated and used by the same connection, avoiding a separate DNS preflight. */
object SourceNetwork {
    private val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = Dns.SYSTEM.lookup(hostname).also { addresses ->
                check(addresses.isNotEmpty() && addresses.all(::publicAddress)) { failed(msg(R.string.network_url_blocked)) }
            }
        }).build()

    internal fun publicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        val b = address.address.map { it.toInt() and 255 }
        return if (b.size == 4) {
            b[0] !in setOf(0, 10, 127) && b[0] < 224 && !(b[0] == 100 && b[1] in 64..127) &&
                !(b[0] == 169 && b[1] == 254) && !(b[0] == 172 && b[1] in 16..31) &&
                !(b[0] == 192 && b[1] == 168) && !(b[0] == 192 && b[1] == 0) &&
                !(b[0] == 198 && b[1] in 18..19) && !(b[0] == 198 && b[1] == 51 && b[2] == 100) &&
                !(b[0] == 203 && b[1] == 0 && b[2] == 113)
        } else b.size == 16 && b[0] in 0x20..0x3f &&
            !(b[0] == 0x20 && b[1] == 0x02) &&
            !(b[0] == 0x20 && b[1] == 0x01 && (b[2] == 0 && b[3] == 0 || b[2] == 0x0d && b[3] == 0xb8))
    }

    fun request(address: String, limit: Int, permits: (String) -> Boolean,
                headers: Map<String, String> = emptyMap(), active: () -> Boolean = { true },
                authentication: (String) -> Map<String, String> = { emptyMap() }): CatalogResponse =
        requestWith(client, address, limit, permits, headers, active, authentication = authentication)

    fun postForm(address: String, fields: Map<String, String>, headers: Map<String, String> = emptyMap()): CatalogResponse =
        requestWith(client, address, 64 * 1024, { it == address }, headers, form = fields)

    internal fun requestWith(transport: OkHttpClient, address: String, limit: Int, permits: (String) -> Boolean,
                headers: Map<String, String> = emptyMap(), active: () -> Boolean = { true },
                clock: () -> Long = System::nanoTime,
                authentication: (String) -> Map<String, String> = { emptyMap() },
                form: Map<String, String>? = null): CatalogResponse {
        require(limit > 0)
        val deadline = clock() + TimeUnit.SECONDS.toNanos(60)
        var url = address
        repeat(6) {
            check(active() && !Thread.currentThread().isInterrupted && clock() < deadline) { failed(msg(R.string.network_deadline)) }
            check(permits(url) && Network.allowed(url, setOf(URI(url).host.orEmpty().lowercase()))) { failed(msg(R.string.network_url_blocked)) }
            // OkHttp can bypass DNS for IP literals; reject them before constructing the request.
            UrlScope.canonical(URI(url).let { URI(it.scheme, it.authority, it.path, null, null).toString() })
            val request = Request.Builder().url(url).header("User-Agent", "FM1FlasherAndroid/${BuildConfig.VERSION_NAME} (+native-android)")
            headers.forEach { (key, value) -> request.header(key, value) }
            // Authentication is never forwarded to a different redirect origin (including a CDN).
            val original = URI(address)
            val destination = URI(url)
            if (destination.scheme.equals(original.scheme, true) && destination.host.equals(original.host, true) && (if (destination.port == -1) 443 else destination.port) == (if (original.port == -1) 443 else original.port))
                authentication(url).forEach { (key, value) -> request.header(key, value) }
            if (form != null) request.post(okhttp3.FormBody.Builder().apply { form.forEach { (key, value) -> add(key, value) } }.build())
            val call = transport.newCall(request.build())
            call.timeout().timeout((deadline - clock()).coerceAtLeast(1), TimeUnit.NANOSECONDS)
            call.execute().use { response ->
                if (response.code in listOf(301, 302, 303, 307, 308)) {
                    check(form == null) { failed(msg("oauth_token_error")) }
                    val location = response.header("Location") ?: failed(msg(R.string.redirect_missing))
                    url = try { URI(url).resolve(location).toString() } catch (_: IllegalArgumentException) { failed(msg(R.string.network_url_blocked)) }
                } else {
                    if (response.code !in listOf(200, 206)) return CatalogResponse(response.code, etag = response.header("ETag"),
                        modified = response.header("Last-Modified"), retryAfter = response.header("Retry-After"))
                    val body = checkNotNull(response.body)
                    check(body.contentLength() <= limit) { failed(msg(R.string.download_too_large)) }
                    val output = ByteArrayOutputStream()
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            check(active() && !Thread.currentThread().isInterrupted && clock() < deadline) { failed(msg(R.string.network_deadline)) }
                            val n = input.read(buffer)
                            if (n < 0) break
                            check(output.size() <= limit - n) { failed(msg(R.string.download_too_large)) }
                            output.write(buffer, 0, n)
                        }
                    }
                    return CatalogResponse(response.code, output.toByteArray(), response.header("ETag"), response.header("Last-Modified"))
                }
            }
        }
        failed(msg(R.string.too_many_redirects))
    }
}
