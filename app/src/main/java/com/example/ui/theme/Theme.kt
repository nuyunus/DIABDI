package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Crisp, Clean Modern Light Color Scheme
private val ModernLightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCFCE7), // Soft mint green
    onPrimaryContainer = Color(0xFF065F46),
    secondary = AmberAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7), // Soft warm amber
    onSecondaryContainer = Color(0xFF92400E),
    background = Color(0xFFF8FAFC), // Crisp slate 50
    surface = Color(0xFFFFFFFF), // Pure white cards
    surfaceVariant = Color(0xFFF1F5F9), // Slate 100
    onSurface = Color(0xFF0F172A), // Slate 900
    onSurfaceVariant = Color(0xFF475569), // Slate 600
    outline = Color(0xFFCBD5E1), // Slate 300
    outlineVariant = Color(0xFFE2E8F0)
)

@Composable
fun SantriScanTheme(
    darkTheme: Boolean = false, // Always pure light theme per user request
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ModernLightColorScheme,
        typography = Typography,
        content = content
    )
}
