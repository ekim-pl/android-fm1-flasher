package pl.ekim.fm1_flasher

fun msg(id: Int, vararg arguments: Any): AppText = msg(androidResourceKeys.getValue(id), *arguments)
fun Throwable.appMessage(fallback: Int): AppText = appMessage(androidResourceKeys.getValue(fallback))
fun entryTitleText(entry: CatalogEntry): AppText =
    if (entry.stock) msg(R.string.stock_name) else AppText.Plain(entry.name)
