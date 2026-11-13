package pl.ekim.fm1_flasher

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.widget.*

class Ui(val context: Context) {
    val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val twoColumns = landscape && context.resources.configuration.screenWidthDp >= 600
    companion object {
        private var darkMode = true
        var BG = Color.rgb(14, 19, 18)
        var PANEL = Color.rgb(25, 34, 31)
        var FIELD = Color.rgb(33, 44, 39)
        var ACCENT = Color.rgb(202, 245, 159)
        var TEXT = Color.rgb(240, 246, 238)
        var MUTED = Color.rgb(159, 181, 168)
        var WARNING = Color.rgb(244, 193, 126)

        fun setDark(dark: Boolean) {
            darkMode = dark
            if (dark) {
                BG = Color.rgb(14, 19, 18); PANEL = Color.rgb(25, 34, 31); FIELD = Color.rgb(33, 44, 39)
                ACCENT = Color.rgb(202, 245, 159); TEXT = Color.rgb(240, 246, 238); MUTED = Color.rgb(159, 181, 168)
                WARNING = Color.rgb(244, 193, 126)
            } else {
                BG = Color.rgb(245, 247, 244); PANEL = Color.WHITE; FIELD = Color.rgb(231, 237, 232)
                ACCENT = Color.rgb(61, 105, 31); TEXT = Color.rgb(29, 39, 32); MUTED = Color.rgb(83, 99, 88)
                WARNING = Color.rgb(142, 76, 0)
            }
        }
    }
    fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    fun row() = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
    fun text(value: String, size: Int = 14, color: Int = TEXT, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); includeFontPadding = false
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    fun label(value: String) = text(value, 11, ACCENT, true).apply { letterSpacing = .11f }
    fun rounded(color: Int, radius: Int = 18, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }
    fun card() = column().apply {
        val padding = dp(if (twoColumns) 12 else 18)
        background = rounded(PANEL); setPadding(padding, padding, padding, padding)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(if (twoColumns) 8 else 14) }
    }
    fun button(label: String, emphasis: Boolean = false, action: () -> Unit) = Button(context).apply {
        text = label; isAllCaps = false; textSize = 13f; minHeight = dp(48); minimumHeight = dp(48)
        setPadding(dp(12), dp(8), dp(12), dp(8)); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf(MUTED, if (emphasis) BG else ACCENT)))
        background = RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), rounded(FIELD, 12).apply {
            setColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                intArrayOf(FIELD, if (emphasis) ACCENT else FIELD)))
        }, null)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        setOnClickListener { action() }
    }
    fun spinner(labels: List<String>) = Spinner(context).apply {
        minimumHeight = dp(48); background = rounded(FIELD, 12); setPadding(dp(8), 0, dp(8), 0)
        adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item, labels) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                (super.getView(position, convertView, parent) as TextView).apply {
                    textSize = 13f; setTextColor(TEXT); isSingleLine = false; maxLines = 2
                    setPadding(paddingLeft, 0, paddingRight, 0)
                }
        }
    }
    fun gap(height: Int) = View(context).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    fun categoryColor(entry: CatalogEntry): Int = when {
        "drums" in entry.kinds -> if (darkMode) Color.rgb(240, 182, 123) else Color.rgb(138, 76, 8)
        "synth" in entry.kinds -> if (darkMode) Color.rgb(173, 184, 247) else Color.rgb(77, 92, 199)
        "groove" in entry.kinds -> ACCENT
        "tracker" in entry.kinds -> if (darkMode) Color.rgb(120, 211, 219) else Color.rgb(0, 118, 129)
        "chords" in entry.kinds -> if (darkMode) Color.rgb(230, 164, 200) else Color.rgb(154, 61, 112)
        else -> MUTED
    }
}
