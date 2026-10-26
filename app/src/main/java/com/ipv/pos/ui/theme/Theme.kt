package com.ipv.pos.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Sistema visual mínimo (Sprint 0): paleta de alto contraste para lectura bajo sol
 * (tiendas cubanas sin climatización/illuminación controlada), tipografía compacta
 * para gama baja. Sin dependencias de diseño externas.
 */
private val IPVGreen = Color(0xFF0F6B45)      // verde principal — identidad IPV
private val IPVGreenDark = Color(0xFF094A2F)
private val IPVAmber = Color(0xFFB26A00)      // alertas de caja/stock
private val IPVRed = Color(0xFFB3261E)        // anulaciones, errores
private val SurfaceTint = Color(0xFFF6FBF7)

private val LightScheme = lightColorScheme(
    primary = IPVGreen,
    onPrimary = Color.White,
    secondary = IPVAmber,
    error = IPVRed,
    background = SurfaceTint,
    surface = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF6FD8A8),
    secondary = Color(0xFFFFD8A8),
)

@Composable
fun IpvTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = Typography(),   // defaults M3 legibles; ajustes por pantalla cuando haga falta
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
        ),
        content = content,
    )
}

val AlertRed = IPVRed
val CashAmber = IPVAmber
