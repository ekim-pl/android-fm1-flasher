package pl.ekim.fm1_flasher

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.*
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import org.commonmark.node.*
import java.io.IOException
import java.net.URI

class AboutPanel(context: Context, private val openLink: (String) -> Unit) : LinearLayout(context) {
    private val ui = Ui(context)
    private val aboutDocument = s(R.string.about_document)
    private val origin = URI("https://fm1-flasher.invalid/$aboutDocument")

    init {
        orientation = VERTICAL
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        addView(ui.row().apply {
            setPadding(ui.dp(20), ui.dp(10), ui.dp(20), ui.dp(10))
            addView(ui.text(s(R.string.tab_about), 20, Ui.TEXT, true), LayoutParams(0, -2, 1f))
            addView(ui.text(s(R.string.about_version, version), 13, Ui.MUTED))
        })
        val content = ui.column().apply {
            setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(24))
            addView(ui.text("", 15).apply {
                text = formatted(readDocument(aboutDocument).orEmpty())
                setTextIsSelectable(true)
                setLinkTextColor(Ui.ACCENT)
                movementMethod = LinkMovementMethod.getInstance()
            })
        }
        addView(ScrollView(context).apply { isFillViewport = true; addView(content) }, LayoutParams(-1, 0, 1f))
    }

    private fun readDocument(path: String): String? =
        try { context.assets.open("docs/$path").bufferedReader().use { it.readText() } }
        catch (_: IOException) { Toast.makeText(context, s(R.string.about_document_unavailable), Toast.LENGTH_LONG).show(); null }

    private fun followLink(destination: String) {
        val uri = origin.resolve(destination).normalize()
        if (uri.scheme == origin.scheme && uri.host == origin.host) {
            val path = uri.path.orEmpty().trimStart('/')
            val source = readDocument(path) ?: return
            AlertDialog.Builder(context).setTitle(path.substringAfterLast('/'))
                .setMessage(formatted(source, path.endsWith(".txt")))
                .setPositiveButton(s(R.string.close), null).show()
        } else if (uri.scheme?.lowercase() in listOf("https", "http", "mailto")) openLink(uri.toString())
    }

    private fun formatted(source: String, plainText: Boolean = false): SpannableStringBuilder {
        val rendered = MarkdownDocument.render(source, plainText)
        val result = SpannableStringBuilder(rendered.text)
        for (range in rendered.ranges) {
            fun style(span: Any) { result.setSpan(span, range.start, range.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
            when (val node = range.node) {
                is Heading -> {
                    style(StyleSpan(Typeface.BOLD))
                    style(RelativeSizeSpan(when (node.level) { 1 -> 1.65f; 2 -> 1.3f; else -> 1.1f }))
                }
                is StrongEmphasis -> style(StyleSpan(Typeface.BOLD))
                is Emphasis -> style(StyleSpan(Typeface.ITALIC))
                is Code, is FencedCodeBlock, is IndentedCodeBlock -> {
                    style(TypefaceSpan("monospace")); style(BackgroundColorSpan(Ui.FIELD)); style(RelativeSizeSpan(.9f))
                }
                is ListItem -> style(LeadingMarginSpan.Standard(0, ui.dp(18)))
                is BlockQuote -> style(QuoteSpan(Ui.ACCENT))
                is Link, is Image -> {
                    val destination = if (node is Link) node.destination else (node as Image).destination
                    style(object : ClickableSpan() { override fun onClick(widget: View) = followLink(destination) })
                }
            }
        }
        return result
    }
}
