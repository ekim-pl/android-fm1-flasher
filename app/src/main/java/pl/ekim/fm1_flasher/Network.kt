package pl.ekim.fm1_flasher

import java.net.URI

object Network {
    val ARTWORK_HOSTS = setOf("thefirmwarehub.com")
    private val hosts = ARTWORK_HOSTS + setOf("api.github.com", "github.com", "raw.githubusercontent.com",
        "release-assets.githubusercontent.com", "objects.githubusercontent.com")

    fun allowed(address: String, permitted: Set<String> = hosts): Boolean = runCatching {
        val uri = URI(address)
        uri.scheme == "https" && uri.host?.lowercase() in permitted && uri.userInfo == null &&
            uri.port in listOf(-1, 443) && uri.rawFragment == null
    }.getOrDefault(false)

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
