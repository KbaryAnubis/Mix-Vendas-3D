package com.mixvendas3d.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MixVendasDarkColorScheme = darkColorScheme(
    primary = V19PurplePrimary,
    onPrimary = Color(0xFF1A0D2E),
    primaryContainer = V19SurfaceChip,
    onPrimaryContainer = V19PurpleSoftText,
    secondary = V19GoldAccent,
    onSecondary = Color(0xFF281905),
    secondaryContainer = Color(0xFF4C3625),
    onSecondaryContainer = Color(0xFFF9D5A0),
    tertiary = V19MintSuccess,
    onTertiary = Color(0xFF0A241B),
    tertiaryContainer = V19SuccessContainer,
    onTertiaryContainer = V19SuccessInk,
    background = V19BackgroundDeep,
    onBackground = V19InkPrimary,
    surface = V19SurfaceCard,
    onSurface = V19InkPrimary,
    surfaceVariant = V19SurfaceInput,
    onSurfaceVariant = V19InkMuted,
    outline = V19OutlineSubtle,
    outlineVariant = V19OutlineInput,
    error = V19ErrorSoft,
    onError = Color(0xFF390D18),
    errorContainer = V19ErrorContainer,
    onErrorContainer = Color(0xFFFFD1D7),
)

private val MixVendasShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun MixVendas3DTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = MixVendasDarkColorScheme,
        typography = MixVendasTypography,
        shapes = MixVendasShapes,
        content = content,
    )
}
