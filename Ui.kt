package com.shield.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object C {
    val BG = Color.parseColor("#10141C")
    val CARD = Color.parseColor("#192030")
    val TEXT = Color.parseColor("#EEF1F7")
    val MUTED = Color.parseColor("#9AA4B8")
    val ACCENT = Color.parseColor("#2F6FED")
    val OK = Color.parseColor("#4CD08A")
    val BAD = Color.parseColor("#FF6B6B")
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.label(t: String, size: Float = 16f, bold: Boolean = false, color: Int = C.TEXT): TextView =
    TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
    }

fun Context.button(t: String, primary: Boolean = true, onClick: () -> Unit): Button {
    val radius = dp(12).toFloat()
    return Button(this).apply {
        text = t; isAllCaps = false; setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            cornerRadius = radius
            setColor(if (primary) C.ACCENT else Color.parseColor("#2A3348"))
        }
        setOnClickListener { onClick() }
    }
}

fun Context.input(hint: String, pin: Boolean = false, multi: Boolean = false): EditText {
    val h = hint
    return EditText(this).apply {
        setHint(h); setHintTextColor(C.MUTED); setTextColor(C.TEXT)
        inputType = when {
            pin -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            multi -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            else -> InputType.TYPE_CLASS_TEXT
        }
        if (multi) { minLines = 3; gravity = Gravity.TOP }
    }
}

fun Context.card(): LinearLayout {
    val radius = dp(14).toFloat()
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply { cornerRadius = radius; setColor(C.CARD) }
    }
}

fun Context.row(vararg vs: View): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    for (v in vs) addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
}

fun Context.page(): Pair<ScrollView, LinearLayout> {
    val sv = ScrollView(this).apply { setBackgroundColor(C.BG) }
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(24), dp(20), dp(40))
    }
    sv.addView(col)
    return sv to col
}

fun LinearLayout.add(v: View, top: Int = 12): View {
    addView(v, LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = context.dp(top) })
    return v
}
