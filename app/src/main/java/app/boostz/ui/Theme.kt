package app.boostz.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Noir légèrement teinté de vert, cartes en verre sombre, accent vert citron néon.
val Bg = Color(0xFF040704)
val Card = Color(0xFF0E120E)
val CardHi = Color(0xFF1A201A)
val Edge = Color(0xFF263026)
val Lime = Color(0xFFA6F22A)
val LimeBright = Color(0xFFD2FF5A)
val LimeDark = Color(0xFF5DBE12)
val TextHi = Color(0xFFF3F6F1)
val TextLo = Color(0xFF97A294)
val Ink = Color(0xFF071006)

val LimeGradient = listOf(LimeBright, Lime, LimeDark)

@Composable
fun BoostTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Lime,
            onPrimary = Ink,
            secondary = LimeBright,
            background = Bg,
            onBackground = TextHi,
            surface = CardHi,
            onSurface = TextHi,
            surfaceVariant = Card,
            onSurfaceVariant = TextLo
        ),
        content = content
    )
}
