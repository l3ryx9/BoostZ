package app.boostz.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Thème sombre premium : noirs teintés de vert, surfaces tonales Material 3,
// accent vert citron nuancé de turquoise, et une touche de violet dans les lueurs d'ambiance.
val Bg = Color(0xFF050806)
val Card = Color(0xFF0F1410)
val CardHi = Color(0xFF1B231D)
val Edge = Color(0xFF2A362C)
val Lime = Color(0xFFA6F22A)
val LimeBright = Color(0xFFD2FF5A)
val LimeDark = Color(0xFF5DBE12)
val Aqua = Color(0xFF2EE6B8)
val AquaDeep = Color(0xFF0F9E8A)
val Violet = Color(0xFF8B7CFF)
val TextHi = Color(0xFFF3F6F1)
val TextLo = Color(0xFF97A294)
val Ink = Color(0xFF071006)

val LimeGradient = listOf(LimeBright, Lime, LimeDark)

/** Dégradé « citron vers turquoise » utilisé pour les éléments actifs. */
val AccentGradient = listOf(LimeBright, Lime, Aqua)

private val BoostColors = darkColorScheme(
    primary = Lime,
    onPrimary = Ink,
    primaryContainer = Color(0xFF2C4A0B),
    onPrimaryContainer = LimeBright,
    secondary = Aqua,
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF0B3B34),
    onSecondaryContainer = Color(0xFFA6F5E3),
    tertiary = Violet,
    onTertiary = Color(0xFF1C1260),
    tertiaryContainer = Color(0xFF332A7A),
    onTertiaryContainer = Color(0xFFE2DEFF),
    background = Bg,
    onBackground = TextHi,
    surface = Color(0xFF0A0F0B),
    onSurface = TextHi,
    surfaceVariant = Card,
    onSurfaceVariant = TextLo,
    surfaceTint = Lime,
    outline = Color(0xFF4A5A4C),
    outlineVariant = Edge,
    surfaceContainerLowest = Color(0xFF040604),
    surfaceContainerLow = Color(0xFF0C110D),
    surfaceContainer = Card,
    surfaceContainerHigh = Color(0xFF161D17),
    surfaceContainerHighest = CardHi
)

/** Bords arrondis généreux partout (menus, boutons, cartes Material). */
private val BoostShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun BoostTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BoostColors,
        shapes = BoostShapes,
        content = content
    )
}
