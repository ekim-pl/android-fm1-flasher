package pl.ekim.fm1_flasher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable private fun SourceColorDot(color: SourceColor) {
    Box(Modifier.size(12.dp).background(Color(color.argb), CircleShape)
        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun SourceColorPicker(sourceId: String, selected: SourceColor?, language: String, enabled: Boolean, select: (SourceColor?) -> Unit) {
    fun t(key: String) = resolveCoreText(msg(key), language)
    Text(t("source_color"), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf<SourceColor?>(null) + SourceColor.entries).forEach { color ->
            FilterChip(selected = selected == color, onClick = { select(color) }, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp),
                label = { Text(t(color?.labelKey ?: "source_color_auto")) },
                leadingIcon = { SourceColorDot(color ?: SourceColor.defaultFor(sourceId)) })
        }
    }
    Text(t("source_color_help"), style = MaterialTheme.typography.bodySmall)
}

@Composable internal fun SourceBadge(entry: CatalogEntry, state: AppSnapshot) {
    val name = state.catalog?.source(entry)?.name ?: if (entry.sourceId == CatalogSource.HUB) CatalogSource.hub.name else entry.sourceId
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SourceColorDot(state.sourceColors[entry.sourceId] ?: SourceColor.defaultFor(entry.sourceId))
        Text(name, style = MaterialTheme.typography.labelMedium)
    }
}
