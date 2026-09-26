package com.walsoup.ditto.data

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class BubbleSize(val displayName: String, val sizeDp: Dp, val iconSizeDp: Dp) {
    COMPACT("Compact", 44.dp, 20.dp),
    STANDARD("Standard", 54.dp, 24.dp),
    COMFORT("Comfort", 64.dp, 28.dp)
}

enum class BubbleShape(val displayName: String) {
    CIRCLE("Circle"),
    SQUIRCLE("Squircle"),
    PILL("Pill");

    fun getShape(sizeDp: Dp): Shape {
        return when (this) {
            CIRCLE -> CircleShape
            SQUIRCLE -> RoundedCornerShape(16.dp)
            PILL -> RoundedCornerShape(sizeDp / 2)
        }
    }
}

enum class BubbleTheme(
    val displayName: String,
    val containerColor: Color,
    val iconColor: Color,
    val borderColor: Color
) {
    SAGE(
        displayName = "Sage",
        containerColor = Color(0xFFE8F1EC),
        iconColor = Color(0xFF2D6A4F),
        borderColor = Color(0xFFE0E6E2)
    ),
    LINEN(
        displayName = "Linen",
        containerColor = Color(0xFFFAF9F6),
        iconColor = Color(0xFF1F2421),
        borderColor = Color(0xFFE5E5E0)
    ),
    TERRACOTTA(
        displayName = "Terracotta",
        containerColor = Color(0xFFFBF0EC),
        iconColor = Color(0xFFD47255),
        borderColor = Color(0xFFF2DCD3)
    ),
    SLATE(
        displayName = "Slate",
        containerColor = Color(0xFFEAEAEA),
        iconColor = Color(0xFF374151),
        borderColor = Color(0xFFDCDCDC)
    )
}
