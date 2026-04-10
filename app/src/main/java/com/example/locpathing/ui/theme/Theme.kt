package com.example.locpathing.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LocPathingColorScheme = darkColorScheme(
    primary            = PastelTeal,
    onPrimary          = Navy900,
    primaryContainer   = Navy600,
    onPrimaryContainer = PastelTeal200,
    secondary          = Amber400,
    onSecondary        = Navy900,
    background         = Navy900,
    onBackground       = White90,
    surface            = SurfaceCard,
    onSurface          = White90,
    onSurfaceVariant   = OnSurfaceVar,
)

@Composable
fun LocPathingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LocPathingColorScheme,
        typography  = Typography,
        content     = content
    )
}