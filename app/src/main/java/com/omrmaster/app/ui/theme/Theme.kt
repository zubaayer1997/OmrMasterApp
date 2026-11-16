package com.omrmaster.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PrimaryBlue = Color(0xFF155EEF)
val SecondaryTeal = Color(0xFF0E9384)
val SurfaceBg = Color(0xFFF6F8FC)
val SuccessGreen = Color(0xFF079455)
val ErrorRed = Color(0xFFD92D20)
val WarningAmber = Color(0xFFDC6803)

private val AppColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E6FF),
    onPrimaryContainer = Color(0xFF0A2E7A),
    secondary = SecondaryTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0F4EE),
    background = SurfaceBg,
    surface = Color.White,
    surfaceVariant = Color(0xFFEEF2F8)
)

@Composable
fun OmrMasterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        content = content
    )
}
