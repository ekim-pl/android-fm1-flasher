package pl.ekim.fm1_flasher

import kotlin.test.*

class SourceColorsTest {
    private class Storage : AppStorage {
        val files = mutableMapOf<String, ByteArray>()
        var failWrites = false
        override fun read(path: String, limit: Int) = files[path]?.also { require(it.size <= limit) }
        override fun write(path: String, bytes: ByteArray) { check(!failWrites); files[path] = bytes }
        override fun remove(path: String) { files.remove(path) }
        override fun size(path: String) = files[path]?.size?.toLong()
        override fun list(path: String) = emptyList<String>()
    }
    @Test fun independentChoicesSurviveRestartAndResetOneSourceOnly() {
        val storage = Storage(); val colors = SourceColors(storage)
        assertTrue(colors.load().isEmpty())
        colors.set(CatalogSource.HUB, SourceColor.BLUE)
        colors.set("private-source", SourceColor.ORANGE)
        val restored = SourceColors(storage)
        assertEquals(mapOf(CatalogSource.HUB to SourceColor.BLUE, "private-source" to SourceColor.ORANGE), restored.load())
        assertEquals(mapOf("private-source" to SourceColor.ORANGE), restored.set(CatalogSource.HUB, null))
        assertEquals(SourceColor.TEAL, SourceColor.defaultFor(CatalogSource.HUB))
        assertNotEquals(SourceColor.defaultFor(CatalogSource.HUB), SourceColor.defaultFor("awesome-fm1"))
        assertEquals(SourceColor.defaultFor("private-source"), SourceColor.defaultFor("private-source"))
    }
    @Test fun unknownOrUnreadablePresentationDataDoesNotBreakTheLibrary() {
        val storage = Storage(); val colors = SourceColors(storage)
        storage.files["source-colors.json"] = """{"known":"BLUE","future":"UNKNOWN_COLOR","invalid":{}}""".encodeToByteArray()
        assertEquals(mapOf("known" to SourceColor.BLUE), colors.load())
        storage.files["source-colors.json"] = "broken".encodeToByteArray()
        assertTrue(colors.load().isEmpty())
    }
    @Test fun failedWriteKeepsPreviousChoicesAndRejectsInvalidIds() {
        val storage = Storage(); val colors = SourceColors(storage)
        colors.set("source", SourceColor.RED); storage.failWrites = true
        assertFails { colors.set("source", SourceColor.BLUE) }
        assertEquals(mapOf("source" to SourceColor.RED), colors.load())
        assertFailsWith<IllegalArgumentException> { colors.set("", SourceColor.RED) }
    }
}
