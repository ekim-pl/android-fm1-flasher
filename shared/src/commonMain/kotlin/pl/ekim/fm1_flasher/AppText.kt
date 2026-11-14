package pl.ekim.fm1_flasher

/** Keep messages untranslated until display, including logs kept across language changes. */
sealed interface AppText {
    data class Resource(val key: String, val arguments: List<Any>) : AppText
    data class Plain(val value: String) : AppText
    data class Joined(val parts: List<AppText>, val separator: String = " ") : AppText
}

fun msg(key: String, vararg arguments: Any): AppText = AppText.Resource(key, arguments.toList())

interface LocalizedFailure { val text: AppText }
class InvalidInput(override val text: AppText) : IllegalArgumentException(), LocalizedFailure
class OperationFailed(override val text: AppText) : IllegalStateException(), LocalizedFailure

fun invalid(text: AppText): Nothing = throw InvalidInput(text)
fun failed(text: AppText): Nothing = throw OperationFailed(text)
fun Throwable.appMessage(fallback: String): AppText = when (this) {
    is LocalizedFailure -> text
    else -> cause?.takeIf { it is LocalizedFailure }?.appMessage(fallback)
        ?: message?.takeIf { it.isNotBlank() }?.let(AppText::Plain) ?: msg(fallback)
}
