package pl.ekim.fm1_flasher

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.view.View

object AppLanguage {
    val tags = listOf("", "pl", "en", "zh-Hans")
    val names = listOf("Polski", "English", "简体中文")

    fun selected(context: Context): String =
        context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()

    fun wrap(context: Context): Context {
        val selected = selected(context)
        val locales = if (selected.isNotBlank()) LocaleList.forLanguageTags(selected)
            else context.getSystemService(LocaleManager::class.java).systemLocales
        val config = Configuration(context.resources.configuration).apply { setLocales(locales) }
        return context.createConfigurationContext(config)
    }

    fun choose(activity: Activity, tag: String) {
        require(tag in tags)
        activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
    }
}

fun Context.s(id: Int, vararg arguments: Any): String = getString(id, *arguments)
fun View.s(id: Int, vararg arguments: Any): String = context.getString(id, *arguments)
fun Context.resolve(text: AppText): String = when (text) {
    is AppText.Plain -> text.value
    is AppText.Joined -> text.parts.joinToString(text.separator) { resolve(it) }
    is AppText.Resource -> getString(text.id, *text.arguments.map { if (it is AppText) resolve(it) else it }.toTypedArray())
}

fun Context.entryTitle(entry: CatalogEntry) = resolve(entryTitleText(entry))
fun Context.entryDescription(entry: CatalogEntry, short: Boolean = false) =
    if (entry.stock) s(R.string.stock_description) else if (short) entry.shortDescription else entry.description

fun Context.categoryLabel(kinds: Set<String>) = FirmwareCategory.entries.filter { it.key in kinds }
    .joinToString(" · ") { s(it.label) }.ifBlank { s(R.string.category_other) }
