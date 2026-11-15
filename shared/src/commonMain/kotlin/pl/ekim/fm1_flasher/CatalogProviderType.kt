package pl.ekim.fm1_flasher

enum class CatalogProviderType(val id: String) {
    THE_FIRMWARE_HUB("thefirmwarehub"), AWESOME_FM1("awesome-fm1"), REPO_URL("repo-url"), PKG_URL("pkg-url"),
    JSON_URL("catalog-json");
    companion object {
        fun legacy(sourceId: String) = when (sourceId) {
            CatalogSource.HUB -> THE_FIRMWARE_HUB
            "awesome-fm1" -> AWESOME_FM1
            else -> JSON_URL
        }
    }
}

enum class OAuthGrant { PKCE, CLIENT_CREDENTIALS }
enum class OAuthClientAuth { BASIC, POST }
data class OAuthConfiguration(
    val grant: OAuthGrant = OAuthGrant.PKCE, val clientId: String = "",
    val authorizationUrl: String = "", val tokenUrl: String = "", val scope: String = "",
    val clientAuth: OAuthClientAuth = OAuthClientAuth.BASIC,
)
