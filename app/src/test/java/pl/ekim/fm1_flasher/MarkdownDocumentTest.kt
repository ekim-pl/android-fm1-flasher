package pl.ekim.fm1_flasher

import org.commonmark.node.*
import org.junit.Assert.*
import org.junit.Test

class MarkdownDocumentTest {
    @Test fun rendersReadmeFormattingAndLinks() {
        val document = MarkdownDocument.render("# About\n\n**Firmware** and `USB MIDI`.\n\n3. Connect\n4. Select\n\n- Offline\n\n[License](LICENSE.md)\n\n```sh\n./gradlew build\n```")
        assertTrue(document.text.startsWith("About\n\nFirmware and USB MIDI."))
        assertTrue(document.text.contains("3. Connect\n4. Select"))
        assertTrue(document.text.contains("• Offline"))
        assertTrue(document.text.contains("./gradlew build"))
        assertFalse(document.text.contains("```"))
        for (kind in listOf(Heading::class.java, StrongEmphasis::class.java, Code::class.java, FencedCodeBlock::class.java)) {
            assertTrue(kind.simpleName, document.ranges.any { kind.isInstance(it.node) })
        }
        val link = document.ranges.single { it.node is Link }
        assertEquals("LICENSE.md", (link.node as Link).destination)
        assertEquals("License", document.text.substring(link.start, link.end))
    }

    @Test fun rawHtmlStaysLiteralAndUnsafeLinksAreNotClickable() {
        val document = MarkdownDocument.render("<script>alert('x')</script>\n\n[unsafe](javascript:alert)\n\n[file](file:///private)\n\n[safe](https://example.org)")
        assertTrue(document.text.contains("<script>alert('x')</script>"))
        assertEquals(listOf("https://example.org"), document.ranges.mapNotNull { (it.node as? Link)?.destination })
    }

    @Test fun plainTextDocumentsKeepLineBreaksAndLiteralMarkup() {
        val source = "License\nCopyright <owner> & contributors"
        val document = MarkdownDocument.render(source, plainText = true)
        assertEquals(source, document.text)
        assertTrue(document.ranges.single().node is IndentedCodeBlock)
        assertEquals(source.length, document.ranges.single().end)
    }
}
