package pl.ekim.fm1_flasher

/** Persisted indices must keep their order for existing devices and history. */
enum class DeviceColor(val argb: Long, val labelKey: String) {
    TEAL(0xFF38BDB0, "device_color_teal"),
    BLUE(0xFF829BEE, "device_color_blue"),
    AMBER(0xFFE6B36A, "device_color_amber"),
    PINK(0xFFD88CB2, "device_color_pink"),
    GREEN(0xFF8FCB87, "device_color_green"),
    SKY(0xFF70BCE1, "device_color_sky");

    companion object {
        fun fromIndex(index: Int): DeviceColor = entries.getOrNull(index) ?: TEAL
    }
}
