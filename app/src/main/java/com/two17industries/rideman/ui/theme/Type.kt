package com.two17industries.rideman.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val RidemanTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 140.sp, letterSpacing = (-2).sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 3.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = 2.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 18.sp),
)
