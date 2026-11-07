package app.boostz.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
import app.boostz.R
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.boostz.audio.EqEngine

@Composable
fun BoostRoot() {
    BoostTheme {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var intro by rememberSaveable { mutableStateOf(true) }
        Box(Modifier.fillMaxSize().background(Bg)) {
            // Lueur verte d'ambiance derrière les boutons.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Lime.copy(alpha = 0.13f), Color.Transparent),
                            center = Offset(540f, 900f),
                            radius = 900f
                        )
                    )
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Header()
                Tabs(tab) { tab = it }
                Box(Modifier.weight(1f).padding(top = 14.dp)) {
                    if (tab == 0) MainScreen() else EqualizerScreen()
                }
            }
            AnimatedVisibility(
                visible = intro,
                enter = EnterTransition.None,
                exit = fadeOut(tween(450))
            ) {
                IntroScreen { intro = false }
            }
        }
    }
}

@Composable
private fun Header() {
    val state by EqEngine.state.collectAsStateWithLifecycle()
    val settings by EqEngine.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val power = rememberPower()
    var menu by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 4.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.logo_boostz),
            contentDescription = "Logo BoostZ",
            modifier = Modifier.size(44.dp).clip(CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White)) { append("Boost") }
                withStyle(SpanStyle(color = Lime)) { append("Z") }
            },
            style = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic)
        )
        Spacer(Modifier.weight(1f))
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Menu", tint = TextHi)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (state.enabled) "Arrêter le moteur" else "Démarrer le moteur") },
                    onClick = {
                        menu = false
                        power(!state.enabled)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Démarrer avec le téléphone") },
                    trailingIcon = {
                        if (settings.startOnBoot) Icon(Icons.Filled.Check, contentDescription = null, tint = Lime)
                    },
                    onClick = { EqEngine.setStartOnBoot(!settings.startOnBoot) }
                )
                DropdownMenuItem(
                    text = { Text("Relancer le moteur") },
                    onClick = {
                        menu = false
                        EqEngine.rebuild()
                    }
                )
            }
        }
    }
}

/** Onglets « Principale » et « Equalizer », comme sur la maquette. */
@Composable
private fun Tabs(selected: Int, onSelect: (Int) -> Unit) {
    val outer = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(outer)
            .background(Card, outer)
            .border(1.dp, Color.White.copy(alpha = 0.07f), outer)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TabItem(
            if (selected == 0) Icons.Filled.Home else Icons.Outlined.Home,
            "Principale", selected == 0, Modifier.weight(1f)
        ) { onSelect(0) }
        TabItem(Icons.Filled.Tune, "Equalizer", selected == 1, Modifier.weight(1f)) { onSelect(1) }
    }
}

@Composable
private fun TabItem(icon: ImageVector, label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .height(50.dp)
            .clip(shape)
            .then(
                if (active) Modifier.background(Brush.verticalGradient(listOf(LimeBright, Lime, LimeDark)), shape)
                else Modifier
            )
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (active) Ink else TextHi, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            fontSize = 17.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) Ink else TextHi
        )
    }
}
