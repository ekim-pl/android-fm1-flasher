package pl.ekim.fm1_flasher

import org.commonmark.node.*
import org.commonmark.parser.Parser
import java.net.URI

data class MarkdownRange(val node: Node, val start: Int, val end: Int)
data class MarkdownText(val text: String, val ranges: List<MarkdownRange>)

object MarkdownDocument {
    private val parser = Parser.builder().build()

    fun render(source: String, plainText: Boolean = false): MarkdownText {
        if (plainText) return MarkdownText(source, listOf(MarkdownRange(IndentedCodeBlock(), 0, source.length)))
        val writer = Writer()
        parser.parse(source).accept(writer)
        return MarkdownText(writer.text.toString(), writer.ranges)
    }

    private class Writer : AbstractVisitor() {
        val text = StringBuilder()
        val ranges = mutableListOf<MarkdownRange>()
        private var listDepth = 0

        private fun breaks(count: Int) {
            if (text.isEmpty()) return
            var trailing = 0
            var i = text.length - 1
            while (i >= 0 && text[i--] == '\n') trailing++
            repeat((count - trailing).coerceAtLeast(0)) { text.append('\n') }
        }

        private fun styled(node: Node, write: () -> Unit) {
            val start = text.length
            write()
            if (text.length > start) ranges += MarkdownRange(node, start, text.length)
        }

        private fun link(node: Node, destination: String) {
            val allowed = try { URI(destination).scheme?.lowercase() in listOf(null, "http", "https", "mailto") }
                catch (_: Exception) { false }
            if (allowed) styled(node) { visitChildren(node) } else visitChildren(node)
        }

        override fun visit(node: Text) { text.append(node.literal) }
        override fun visit(node: SoftLineBreak) { text.append(' ') }
        override fun visit(node: HardLineBreak) { text.append('\n') }
        override fun visit(node: Paragraph) { visitChildren(node); breaks(if (node.parent is ListItem) 1 else 2) }
        override fun visit(node: Heading) { breaks(2); styled(node) { visitChildren(node) }; breaks(2) }
        override fun visit(node: Emphasis) = styled(node) { visitChildren(node) }
        override fun visit(node: StrongEmphasis) = styled(node) { visitChildren(node) }
        override fun visit(node: Code) = styled(node) { text.append(node.literal) }
        override fun visit(node: Link) = link(node, node.destination)
        override fun visit(node: Image) = link(node, node.destination)
        override fun visit(node: HtmlInline) { text.append(node.literal) }
        override fun visit(node: HtmlBlock) { text.append(node.literal); breaks(2) }
        override fun visit(node: FencedCodeBlock) { breaks(2); styled(node) { text.append(node.literal) }; breaks(2) }
        override fun visit(node: IndentedCodeBlock) { breaks(2); styled(node) { text.append(node.literal) }; breaks(2) }
        override fun visit(node: BlockQuote) { breaks(2); styled(node) { visitChildren(node) }; breaks(2) }
        override fun visit(node: ThematicBreak) { breaks(2); text.append("────────"); breaks(2) }

        private fun list(node: Node) { breaks(1); listDepth++; visitChildren(node); listDepth--; breaks(2) }
        override fun visit(node: BulletList) = list(node)
        override fun visit(node: OrderedList) = list(node)
        override fun visit(node: ListItem) {
            breaks(1)
            styled(node) {
                text.append("  ".repeat((listDepth - 1).coerceAtLeast(0)))
                val parent = node.parent
                if (parent is OrderedList) {
                    var number = parent.markerStartNumber ?: 1
                    var sibling = node.previous
                    while (sibling != null) { number++; sibling = sibling.previous }
                    text.append("$number. ")
                } else text.append("• ")
                visitChildren(node)
                breaks(1)
            }
        }
    }
}
