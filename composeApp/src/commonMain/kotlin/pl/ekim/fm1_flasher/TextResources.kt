package pl.ekim.fm1_flasher

internal fun resolveCoreText(text: AppText, language: String): String = when (text) {
    is AppText.Plain -> text.value
    is AppText.Joined -> text.parts.joinToString(text.separator) { resolveCoreText(it, language) }
    is AppText.Resource -> {
        val template = coreStrings[language]?.get(text.key) ?: coreStrings.getValue("en")[text.key] ?: text.key
        Regex("%(\\d+)\\$[sd]").replace(template) { match ->
            val argument = text.arguments.getOrNull(match.groupValues[1].toInt() - 1)
            if (argument is AppText) resolveCoreText(argument, language) else argument.toString()
        }.replace("%%", "%")
    }
}
