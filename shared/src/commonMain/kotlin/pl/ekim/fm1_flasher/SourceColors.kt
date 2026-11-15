package pl.ekim.fm1_flasher

import kotlinx.serialization.json.*

/** Local presentation choices only; never part of a provider request or compatibility policy. */
enum class SourceColor(val argb: Long, val labelKey: String) {
    TEAL(0xFF00897B, "source_color_teal"), BLUE(0xFF1565C0, "source_color_blue"),
    PURPLE(0xFF7B1FA2, "source_color_purple"), ORANGE(0xFFB45309, "source_color_orange"),
    RED(0xFFC62828, "source_color_red"), PINK(0xFFAD1457, "source_color_pink"),
    OLIVE(0xFF556B2F, "source_color_olive"), SLATE(0xFF546E7A, "source_color_slate");
    companion object {
        fun defaultFor(sourceId: String): SourceColor = when (sourceId) {
            CatalogSource.HUB -> TEAL
            "awesome-fm1" -> PURPLE
            else -> entries[sourceId.fold(0) { hash, character -> hash * 31 + character.code }.mod(entries.size)]
        }
    }
}
class SourceColors(private val storage: AppStorage) {
    fun load(): Map<String, SourceColor> = runCatching {
        val bytes = storage.read("source-colors.json", 128000) ?: return emptyMap()
        Json.parseToJsonElement(bytes.decodeToString()).jsonObject.mapNotNull { (id, value) ->
            SourceColor.entries.firstOrNull { it.name == (value as? JsonPrimitive)?.contentOrNull }?.let { id to it }
        }.toMap()
    }.getOrDefault(emptyMap()) // Unreadable optional display preferences use default colors.

    fun set(sourceId: String, color: SourceColor?): Map<String, SourceColor> {
        require(sourceId.isNotBlank() && sourceId.length <= 256)
        val next = load().toMutableMap()
        if (color == null) next.remove(sourceId) else next[sourceId] = color
        storage.write("source-colors.json", buildJsonObject {
            next.entries.sortedBy { it.key }.forEach { (id, value) -> put(id, value.name) }
        }.toString().encodeToByteArray())
        return next
    }
}
