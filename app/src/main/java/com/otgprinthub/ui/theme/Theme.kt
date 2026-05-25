package com.otgprinthub.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GlassColorScheme = darkColorScheme(
    primary              = GlassPrimary,
    onPrimary            = Color(0xFF001A60),
    primaryContainer     = Color(0x3382B1FF),
    onPrimaryContainer   = Color(0xFFD6E4FF),
    secondary            = GlassSecondary,
    onSecondary          = Color(0xFF003640),
    secondaryContainer   = Color(0x3380DEEA),
    onSecondaryContainer = Color(0xFFCCF9FF),
    tertiary             = GlassAccent,
    onTertiary           = Color(0xFF2D1B69),
    tertiaryContainer    = Color(0x33A78BFA),
    onTertiaryContainer  = Color(0xFFEDE9FE),
    // Transparent background so the GradientBackground in MainActivity shows through Scaffolds
    background           = Color.Transparent,
    onBackground         = GlassOnSurface,
    // Opaque dark surface so dropdowns / menus / dialogs remain legible
    surface              = Color(0xFF1E1B3A),
    onSurface            = GlassOnSurface,
    surfaceVariant       = Color(0xFF2A2750),
    onSurfaceVariant     = GlassOnSurfaceVar,
    outline              = GlassBorder,
    outlineVariant       = GlassBorderSubtle,
    error                = GlassError,
    onError              = Color(0xFF5C000C),
    errorContainer       = GlassErrorContainer,
    onErrorContainer     = Color(0xFFFFDADF),
    scrim                = Color(0xCC000000),
)

@Composable
fun OtgPrintHubTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GlassColorScheme,
        typography = Typography,
        content = content
    )
}
