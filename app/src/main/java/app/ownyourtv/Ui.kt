package app.ownyourtv

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** The few styled building blocks every screen is made of. */
class Ui(private val ctx: Context) {
    companion object {
        val INK = Color.rgb(11, 13, 18)
        val CARD = Color.rgb(22, 26, 35)
        val CARD_ON = Color.rgb(31, 36, 48)
        val PAPER = Color.rgb(244, 244, 240)
        val GREY = Color.rgb(150, 156, 170)
        val DIM = Color.rgb(90, 96, 110)
        val LIME = Color.rgb(198, 244, 50)
        val RED = Color.rgb(255, 84, 84)
    }

    val display: Typeface = runCatching { Typeface.createFromAsset(ctx.assets, "fonts/display.ttf") }.getOrDefault(Typeface.DEFAULT_BOLD)
    val mono: Typeface = Typeface.MONOSPACE

    fun dp(v: Number) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    fun rounded(color: Int, radius: Number, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (stroke > 0) setStroke(dp(stroke), strokeColor)
    }

    fun text(s: CharSequence, size: Number, color: Int = PAPER, face: Typeface? = null, bold: Boolean = false) = TextView(ctx).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size.toFloat())
        setTextColor(color)
        typeface = face ?: if (bold) Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.create("sans-serif", Typeface.NORMAL)
        includeFontPadding = false
        setLineSpacing(0f, 1.15f)
    }

    fun title(s: CharSequence, size: Number = 40) = text(s, size, PAPER, display).apply { letterSpacing = -0.02f }

    fun chip(s: CharSequence, color: Int = LIME) = text(s.toString().uppercase(), 12, color, mono).apply { letterSpacing = 0.12f }

    fun col(gap: Int = 0) = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; if (gap > 0) dividerDrawable = spacer(gap); showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE }

    fun row(gap: Int = 0) = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; if (gap > 0) dividerDrawable = spacer(gap, true); showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE }

    private fun spacer(gap: Int, horizontal: Boolean = false) = GradientDrawable().apply {
        setColor(Color.TRANSPARENT); if (horizontal) setSize(dp(gap), 1) else setSize(1, dp(gap))
    }

    /** Grows a little and lights up when the remote lands on it. */
    fun focusable(v: View, normal: Int = CARD, focused: Int = CARD_ON, radius: Int = 20, ring: Boolean = true): View {
        v.isFocusable = true
        v.isClickable = true
        v.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), rounded(focused, radius, if (ring) 3 else 0, LIME))
            addState(intArrayOf(), rounded(normal, radius))
        }
        v.setOnFocusChangeListener { view, has ->
            view.animate().scaleX(if (has) 1.035f else 1f).scaleY(if (has) 1.035f else 1f).setDuration(140).start()
        }
        return v
    }

    fun button(s: CharSequence, primary: Boolean, onClick: () -> Unit) = text(s, 18, if (primary) INK else PAPER, bold = true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(28), dp(14), dp(28), dp(14))
        focusable(this, if (primary) LIME else Color.argb(18, 255, 255, 255), if (primary) Color.WHITE else Color.argb(40, 255, 255, 255), 14, !primary)
        setOnClickListener { onClick() }
    }

    /** A rounded check box: lime with a tick when on. */
    fun check(on: Boolean) = TextView(ctx).apply {
        text = if (on) "✓" else ""
        gravity = Gravity.CENTER
        setTextColor(INK)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        typeface = Typeface.DEFAULT_BOLD
        background = if (on) rounded(LIME, 8) else rounded(Color.TRANSPARENT, 8, 2, DIM)
        layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
    }

    fun ellipsize(t: TextView, lines: Int = 1) = t.apply { maxLines = lines; ellipsize = TextUtils.TruncateAt.END }

    fun lp(w: Int = ViewGroup.LayoutParams.WRAP_CONTENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, weight: Float = 0f) =
        LinearLayout.LayoutParams(w, h, weight)

    fun frame() = FrameLayout(ctx)
}
