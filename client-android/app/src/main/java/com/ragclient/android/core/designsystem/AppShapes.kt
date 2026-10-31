package com.ragclient.android.core.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object AppShapes {

    val Card = RoundedCornerShape(20.dp)
    val Pill = CircleShape
    val PromptBar = RoundedCornerShape(28.dp)

    val Shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = Card,
        large = PromptBar,
        extraLarge = RoundedCornerShape(36.dp)
    )
}
