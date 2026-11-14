package pl.ekim.fm1_flasher

import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URL
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection

object Network {
    val ARTWORK_HOSTS = setOf("thefirmwarehub.com")
    private val hosts = ARTWORK_HOSTS + setOf("api.github.com", "github.com", "raw.githubusercontent.com",
        "release-assets.githubusercontent.com", "objects.githubusercontent.com")
    private val timer = Timer("download-deadlines", true)

    fun allowed(address: String, permitted: Set<String> = hosts): Boolean = runCatching {
        val uri = URI(address)
        uri.scheme == "https" && uri.host?.lowercase() in permitted && uri.userInfo == null &&
            uri.port in listOf(-1, 443) && uri.rawFragment == null
    }.getOrDefault(false)

    fun get(address: String, limit: Int, permitted: Set<String> = hosts): ByteArray =
        fetch(address, limit, permitted, 60_000, { it.openConnection() as HttpsURLConnection })

    internal fun fetch(address: String, limit: Int, permitted: Set<String>, timeoutMs: Long,
                       open: (URL) -> HttpsURLConnection,
                       now: () -> Long = { System.nanoTime() / 1_000_000 }): ByteArray {
        require(limit > 0 && timeoutMs > 0)
        val deadline = now() + timeoutMs
        val active = AtomicReference<HttpsURLConnection?>()
        val timeout = object : TimerTask() { override fun run() { runCatching { active.get()?.disconnect() } } }
        timer.schedule(timeout, timeoutMs)
        fun remaining(): Int {
            check(!Thread.currentThread().isInterrupted && now() < deadline) { failed(msg(R.string.network_deadline)) }
            return (deadline - now()).coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
        }
        var url = URL(address)
        try {
            repeat(6) {
                remaining()
                check(allowed(url.toString(), permitted)) { failed(msg(R.string.network_url_blocked)) }
                val connection = open(url)
                active.set(connection)
                try {
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = minOf(15_000, remaining())
                    connection.readTimeout = minOf(20_000, remaining())
                    connection.setRequestProperty("User-Agent", "FM1FlasherAndroid/${BuildConfig.VERSION_NAME} (+native-android)")
                    val code = connection.responseCode
                    remaining()
                    if (code in listOf(301, 302, 303, 307, 308)) {
                        url = URL(url, connection.getHeaderField("Location") ?: failed(msg(R.string.redirect_missing)))
                    } else {
                        check(code == 200) { failed(msg(R.string.http_error, code)) }
                        check(connection.contentLengthLong <= limit) { failed(msg(R.string.download_too_large)) }
                        return connection.inputStream.use { input ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                connection.readTimeout = minOf(20_000, remaining())
                                val count = input.read(buffer)
                                remaining()
                                if (count < 0) break
                                check(output.size() <= limit - count) { failed(msg(R.string.download_too_large)) }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                    }
                } finally { active.set(null); connection.disconnect() }
            }
            failed(msg(R.string.too_many_redirects))
        } finally { timeout.cancel(); timer.purge() }
    }
}

/** Bound parser recursion and token size before Android's recursive JSON parser sees remote input. */
internal object JsonLimits {
    fun check(text: String) {
        var depth = 0
        var quoted = false
        var escaped = false
        var token = 0
        for (c in text) {
            if (quoted) {
                token++
                require(token <= 32_768) { invalid(msg(R.string.catalog_invalid)) }
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') { quoted = false; token = 0 }
            } else when (c) {
                '"' -> { quoted = true; token = 0 }
                '{', '[' -> { depth++; token = 0; require(depth <= 32) { invalid(msg(R.string.catalog_invalid)) } }
                '}', ']' -> { depth--; token = 0 }
                ',', ':', ' ', '\n', '\r', '\t' -> token = 0
                else -> {
                    require(c != '\'' && c != '/' && c != '#') { invalid(msg(R.string.catalog_invalid)) }
                    token++; require(token <= 256) { invalid(msg(R.string.catalog_invalid)) }
                }
            }
        }
    }
}
