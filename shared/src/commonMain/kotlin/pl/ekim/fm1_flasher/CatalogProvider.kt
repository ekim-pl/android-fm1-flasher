package pl.ekim.fm1_flasher

data class CatalogResponse(val code: Int, val bytes: ByteArray = byteArrayOf(), val etag: String? = null,
    val modified: String? = null, val retryAfter: String? = null)
data class CatalogSnapshot(val entries: List<CatalogEntry>?, val updated: String, val license: String,
    val etag: String?, val modified: String?)

/** Providers interpret a source; transport, credentials, scheduling and storage stay with the host. */
interface CatalogProvider {
    val type: CatalogProviderType
    fun endpoint(source: CatalogSource): String = source.url
    fun decode(source: CatalogSource, response: CatalogResponse, previous: List<CatalogEntry>,
               get: (String, Map<String, String>) -> CatalogResponse): CatalogSnapshot
}
