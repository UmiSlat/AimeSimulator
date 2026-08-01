package io.github.umislat.aimesimulator.ui

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import com.google.android.material.color.MaterialColors
import io.github.umislat.aimesimulator.R

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

internal fun View.applyMargins(horizontal: Int = 0, vertical: Int = 0) {
    layoutParams = (layoutParams as? ViewGroup.MarginLayoutParams ?: ViewGroup.MarginLayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )).apply {
        setMargins(context.dp(horizontal), context.dp(vertical), context.dp(horizontal), context.dp(vertical))
    }
}

internal fun Context.sectionTitle(@StringRes text: Int): TextView = TextView(this).apply {
    setText(text)
    textSize = 14f
    setTypeface(typeface, Typeface.BOLD)
    setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary))
    setPadding(dp(4), dp(20), dp(4), dp(8))
}

internal fun Context.bodyText(@StringRes text: Int): TextView = TextView(this).apply {
    setText(text)
    textSize = 16f
    setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
}

internal fun verticalLayout(context: Context): LinearLayout = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(context.dp(20), context.dp(8), context.dp(20), context.dp(24))
}

internal fun Context.stateLabel(active: Boolean): String = getString(
    if (active) R.string.state_active else R.string.state_inactive
)
