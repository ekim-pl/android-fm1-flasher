package pl.ekim.fm1_flasher

/** Keep messages untranslated until display, including logs kept across language changes. */
sealed interface AppText {
    data class Resource(val id: Int, val arguments: List<Any>) : AppText
    data class Plain(val value: String) : AppText
    data class Joined(val parts: List<AppText>, val separator: String = " ") : AppText
}

fun msg(id: Int, vararg arguments: Any): AppText = AppText.Resource(id, arguments.toList())

interface LocalizedFailure { val text: AppText }
class InvalidInput(override val text: AppText) : IllegalArgumentException(), LocalizedFailure
class OperationFailed(override val text: AppText) : IllegalStateException(), LocalizedFailure

fun invalid(text: AppText): Nothing = throw InvalidInput(text)
fun failed(text: AppText): Nothing = throw OperationFailed(text)
fun Throwable.appMessage(fallback: Int): AppText = when (this) {
    is LocalizedFailure -> text
    else -> cause?.takeIf { it is LocalizedFailure }?.appMessage(fallback)
        ?: message?.takeIf { it.isNotBlank() }?.let(AppText::Plain) ?: msg(fallback)
}

fun entryTitleText(entry: CatalogEntry): AppText =
    if (entry.stock) msg(R.string.stock_name) else AppText.Plain(entry.name)
