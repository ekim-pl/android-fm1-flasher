package pl.ekim.fm1_flasher

data class LocalPackage(val entry: CatalogEntry, val downloadedAt: Long, val bytes: Long)
data class LibrarySettings(val favorites: Set<String> = emptySet(), val offline: Boolean = false, val selectedId: String? = null)
