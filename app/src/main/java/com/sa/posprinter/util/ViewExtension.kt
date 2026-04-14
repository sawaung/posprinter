package com.sa.posprinter.util

import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.card.MaterialCardView

// For TextView
fun TextView.showIfNotEmpty(text: String?) {
    visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    if (!text.isNullOrEmpty()) this.text = text
}

// For any View (Layouts, CardViews, etc.)
fun View.showIfValueNotNull(value: Any?) {
    visibility = when {
        value == null -> View.GONE
        value is Double && value == 0.0 -> View.GONE
        value is String && value.isEmpty() -> View.GONE
        else -> View.VISIBLE
    }
}

// For numeric values with custom zero check
fun View.showIfNonZero(value: Double?) {
    visibility = if (value == null || value == 0.0) View.GONE else View.VISIBLE
}

// Combined text and layout hiding
fun TextView.showAndSetText(text: String?) {
    visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    text?.let { if (it.isNotEmpty()) this.text = it }
}