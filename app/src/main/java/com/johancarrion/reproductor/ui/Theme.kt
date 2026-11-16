package com.johancarrion.reproductor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.johancarrion.reproductor.Graph

@Immutable
data class Palette(
    val bg: Color,
    val surface: Color,
    val player: Color,
    val text: Color,
    val muted: Color,
    val chip: Color,
    val accent: Color,
    val dark: Boolean
)

val AccentColors = listOf(
    Color(0xFF2F9BFF),
    Color(0xFF2ECC71),
    Color(0xFFFF5C8A),
    Color(0xFFFF9A3D),
    Color(0xFF9B6CFF)
)

fun accentName(index: Int, en: Boolean): String {
    val es = listOf("Azul", "Verde", "Rosa", "Naranja", "Morado")
    val enNames = listOf("Blue", "Green", "Pink", "Orange", "Purple")
    val i = index.coerceIn(0, es.size - 1)
    return if (en) enNames[i] else es[i]
}

fun makePalette(theme: String, accentIndex: Int): Palette {
    val a = AccentColors[accentIndex.coerceIn(0, AccentColors.size - 1)]
    return when (theme) {
        "amoled" -> Palette(
            bg = Color(0xFF000000), surface = Color(0xFF111112), player = Color(0xFF0A0A0B),
            text = Color(0xFFF2F2F3), muted = Color(0xFF9A9AA0), chip = Color(0xFF161618),
            accent = a, dark = true
        )
        "light" -> Palette(
            bg = Color(0xFFF6F6F8), surface = Color(0xFFFFFFFF), player = Color(0xFFECECF0),
            text = Color(0xFF141416), muted = Color(0xFF6B6B73), chip = Color(0xFFE6E6EC),
            accent = a, dark = false
        )
        else -> Palette(
            bg = Color(0xFF0B0B0C), surface = Color(0xFF1B1B1D), player = Color(0xFF2A2A2C),
            text = Color(0xFFF2F2F3), muted = Color(0xFF9A9AA0), chip = Color(0xFF1F1F22),
            accent = a, dark = true
        )
    }
}

val LocalPalette = staticCompositionLocalOf { makePalette("classic", 0) }
val LocalLang = staticCompositionLocalOf { "es" }

@Composable
fun ReproductorTheme(content: @Composable () -> Unit) {
    val theme by Graph.settings.theme.flow.collectAsState()
    val accent by Graph.settings.accent.flow.collectAsState()
    val lang by Graph.settings.lang.flow.collectAsState()
    val uiScale by Graph.settings.uiScale.flow.collectAsState()
    val p = makePalette(theme, accent)
    // Se parte de la densidad y el tamaño de letra reales del sistema (DPI, "Tamaño de fuente")
    // y se aplica encima la escala de la app, así sigue reaccionando a los cambios de DPI.
    val sys = LocalDensity.current
    val density = Density(sys.density * uiScale, sys.fontScale)
    val scheme = if (p.dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = Color.White,
            background = p.bg, onBackground = p.text,
            surface = p.surface, onSurface = p.text,
            surfaceVariant = p.chip, onSurfaceVariant = p.muted
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = Color.White,
            background = p.bg, onBackground = p.text,
            surface = p.surface, onSurface = p.text,
            surfaceVariant = p.chip, onSurfaceVariant = p.muted
        )
    }
    CompositionLocalProvider(
        LocalPalette provides p,
        LocalLang provides lang,
        LocalDensity provides density
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
