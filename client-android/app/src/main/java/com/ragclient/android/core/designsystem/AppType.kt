package com.ragclient.android.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Answer-first typography: the streaming answer is the hero
 * (large, serif), chrome is quiet sans. Client plan section 5.
 */
object AppType {

    val Display = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.2).sp
    )

    val AnswerBody = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 19.sp,
        lineHeight = 30.sp
    )

    val Label = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.sp
    )

    val Body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    )

    val Typography = Typography(
        displayLarge = Display,
        bodyLarge = AnswerBody,
        bodyMedium = Body,
        labelSmall = Label,
        labelMedium = Label
    )
}
