package pl.ekim.fm1_flasher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

@Composable internal fun AboutScreen(
    document: String, appName: String, version: String, platformLimits: String?,
    privacyTitle: String, privacySummary: String, privacyAction: String,
    onPrivacy: () -> Unit, open: (String) -> Unit,
) {
    val sections = remember(document) { document.trim().split(Regex("(?m)^## ")) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 840.dp).fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(appName, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = .7f)) {
                            Text(version, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
                        }
                        MarkdownText(sections.first().substringAfter('\n').trim(), open)
                        if (platformLimits != null) Text(platformLimits, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                OutlinedCard {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(privacyTitle, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                        Text(privacySummary, style = MaterialTheme.typography.bodyMedium)
                        FilledTonalButton(onPrivacy) { Text(privacyAction) }
                    }
                }
            }
            sections.drop(1).forEach { section ->
                item {
                    val title = section.substringBefore('\n').trim()
                    Card(colors = CardDefaults.cardColors(containerColor = if (title == "Awesome FM-1")
                        MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.semantics { heading() })
                            MarkdownText(section.substringAfter('\n', "").trim(), open)
                        }
                    }
                }
            }
        }
    }
}

/** Readable Markdown blocks for bundled guides and catalog descriptions. */
@Composable internal fun MarkdownText(markdown: String, open: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        markdown.trim().split(Regex("\n\\s*\n")).filter { it.isNotBlank() }.forEach { block ->
            val value = block.trim()
            when {
                value.startsWith("#") -> Text(
                    markdownInline(value.replace(Regex("^#{1,6}\\s+"), ""), open),
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() },
                )
                value.lines().all { it.trimStart().startsWith("- ") } -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    value.lines().forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("•", color = MaterialTheme.colorScheme.primary)
                            Text(markdownInline(line.trimStart().removePrefix("- "), open), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                else -> Text(markdownInline(value, open), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable private fun markdownInline(value: String, open: (String) -> Unit): AnnotatedString {
    val tokens = Regex("\\[([^]]+)\\]\\(([^ )]+)\\)|\\*\\*(.+?)\\*\\*|`([^`]+)`")
    val linkStyle = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)
    val codeStyle = SpanStyle(fontFamily = FontFamily.Monospace, background = MaterialTheme.colorScheme.surfaceVariant)
    return buildAnnotatedString {
        var end = 0
        tokens.findAll(value).forEach { token ->
            append(value.substring(end, token.range.first))
            when {
                token.groupValues[2].isNotEmpty() -> withLink(LinkAnnotation.Clickable(
                    tag = token.groupValues[2], styles = TextLinkStyles(style = linkStyle),
                    linkInteractionListener = { open(token.groupValues[2]) },
                )) { append(token.groupValues[1]) }
                token.groupValues[3].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(token.groupValues[3]) }
                else -> withStyle(codeStyle) { append(token.groupValues[4]) }
            }
            end = token.range.last + 1
        }
        append(value.substring(end))
    }
}
