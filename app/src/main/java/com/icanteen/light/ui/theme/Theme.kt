package com.icanteen.light.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// ─────────────────────────────────────────────────────────────
// PREMIUM DARK — Slate / Teal (inspired by logo)
// Rule: The palette should never feel "candy". Only one accent.
// ─────────────────────────────────────────────────────────────
private val DarkScheme = darkColorScheme(
    background         = Color(0xFF0B1017), // deepest ink — almost black
    surface            = Color(0xFF131C26), // card surface — barely lighter
    surfaceVariant     = Color(0xFF1E2D3D), // elevated content blocks
    onBackground       = Color(0xFFE2EAF4), // body text — warm off-white
    onSurface          = Color(0xFFE2EAF4),
    onSurfaceVariant   = Color(0xFF8A9DBB), // muted / dim labels

    // Teal accent — single accent pulled from logo glow
    primary            = Color(0xFF2DD4BF), // teal-400: vivid but not neon
    onPrimary          = Color(0xFF021A18),
    primaryContainer   = Color(0xFF0D4F47), // dark teal for subtle chips/badges
    onPrimaryContainer = Color(0xFFCCF7F2),

    // Neutral secondary — no competing hue
    secondary          = Color(0xFF94A3B8), // slate-400
    onSecondary        = Color(0xFF0B1017),
    secondaryContainer = Color(0xFF1E2D3D), // same as surfaceVariant — unobtrusive
    onSecondaryContainer = Color(0xFFCBD5E1),

    // Error: refined rose — elegant, not brutal
    error              = Color(0xFFF87171), // rose-400
    onError            = Color(0xFF450A0A),
    errorContainer     = Color(0xFF7F1D1D),
    onErrorContainer   = Color(0xFFFECACA),

    // Used for StatisticsPanel urgent state
    tertiary           = Color(0xFFFBBF24), // amber-400 for warnings
    onTertiary         = Color(0xFF0B1017),
    tertiaryContainer  = Color(0xFF451A03),
    onTertiaryContainer = Color(0xFFFDE68A)
)

// ─────────────────────────────────────────────────────────────
// PREMIUM LIGHT — Crisp white / deep teal
// Philosophy: pure white surfaces, deep ink text, one strong teal.
// Every color pair must pass WCAG AA (4.5:1 contrast minimum).
// ─────────────────────────────────────────────────────────────
private val LightScheme = lightColorScheme(
    // Pure white canvas — no grey wash that muddies readability
    background         = Color(0xFFFFFFFF),
    surface            = Color(0xFFF8FAFC), // Slate-50: cards just barely lifted
    surfaceVariant     = Color(0xFFE2EBF3), // Slate-200: dividers, secondary blocks
    onBackground       = Color(0xFF0D1B2A), // Near-black — maximum readability
    onSurface          = Color(0xFF0D1B2A),
    onSurfaceVariant   = Color(0xFF445668), // Slate-600 — dim labels, still legible

    // Deep teal — readable on both white and tinted surfaces
    primary            = Color(0xFF0B6E65), // Teal-800: strong, corporate, legible
    onPrimary          = Color(0xFFFFFFFF),
    primaryContainer   = Color(0xFF0D9488), // Teal-600: chips/badges bg (dark enough)
    onPrimaryContainer = Color(0xFFFFFFFF), // White text on teal container — max contrast

    // Muted blue-grey secondary
    secondary          = Color(0xFF445668), // Slate-600
    onSecondary        = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE6EF), // Slate-200ish: subtle bg
    onSecondaryContainer = Color(0xFF0D1B2A),

    // Error — deep rose, readable on white
    error              = Color(0xFFB91C1C), // Red-700
    onError            = Color(0xFFFFFFFF),
    errorContainer     = Color(0xFFFEE2E2), // Red-100: alert background (light)
    onErrorContainer   = Color(0xFF7F1D1D), // Red-900: text on alert bg — max contrast

    // Tertiary (unused in current UI, safe fallback)
    tertiary           = Color(0xFF92400E),
    onTertiary         = Color(0xFFFFFFFF),
    tertiaryContainer  = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF451A03)
)

// Shapes — precise, not bubbly
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun CanteenTheme(
    darkMode: String = "Systémový",
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (darkMode) {
        "Světlý" -> false
        "Tmavý"  -> true
        else     -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDarkTheme) DarkScheme else LightScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDarkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes      = AppShapes,
        typography  = Typography(),
        content     = content
    )
}
