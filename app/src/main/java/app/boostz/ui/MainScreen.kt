package app.boostz.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.boostz.audio.BASS_MAX_DB
import app.boostz.audio.EqEngine
import app.boostz.audio.LOUDNESS_MAX_DB
import app.boostz.audio.NowPlaying
import app.boostz.audio.SmartEq
import kotlin.math.roundToInt

private fun dbText(db: Float): String =
    if (db < 0.05f) "0 dB" else "+" + String.format("%.1f", db).removeSuffix(",0").removeSuffix(".0") + " dB"

@Composable
fun MainScreen() {
    val s by EqEngine.state.collectAsStateWithLifecycle()
    val settings by EqEngine.settings.collectAsStateWithLifecycle()
    val smart by SmartEq.info.collectAsStateWithLifecycle()
    val levels by SmartEq.levels.collectAsStateWithLifecycle()
    val track by NowPlaying.track.collectAsStateWithLifecycle()
    val access by NowPlaying.access.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val ensureOn = rememberEnsureOn()

    val analysis = rememberAnalysisAccess()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ------------------------------------------------ les deux gros boutons
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            WaveBars(
                levels, s.enabled,
                Modifier.align(Alignment.CenterStart).width(64.dp).height(170.dp)
            )
            WaveBars(
                levels.reversed(), s.enabled,
                Modifier.align(Alignment.CenterEnd).width(64.dp).height(170.dp)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BigKnob(
                    value = s.loudness / 100f,
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = "Volume\nBooster",
                    valueText = dbText(s.loudness / 100f * LOUDNESS_MAX_DB),
                    active = s.enabled,
                    modifier = Modifier.fillMaxWidth(0.68f)
                ) {
                    ensureOn()
                    EqEngine.setLoudness((it * 100f).roundToInt())
                }
                BigKnob(
                    value = s.bass / 1000f,
                    icon = Icons.Filled.GraphicEq,
                    title = "Bass\nBooster",
                    valueText = dbText(s.bass / 1000f * BASS_MAX_DB),
                    active = s.enabled,
                    modifier = Modifier.fillMaxWidth(0.68f).offset(y = (-18).dp)
                ) {
                    ensureOn()
                    EqEngine.setBass((it * 1000f).roundToInt())
                }
            }
        }

        // ------------------------------------------------ Auto EQ
        GlassCard(modifier = Modifier.offset(y = (-14).dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AiBadge(56)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Auto EQ (Intelligent)", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextHi)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Ajuste automatiquement l'EQ selon la musique, le volume et les basses.",
                        fontSize = 12.sp, lineHeight = 16.sp, color = TextLo
                    )
                }
                Spacer(Modifier.width(8.dp))
                LimeSwitch(settings.smartEq) { on ->
                    EqEngine.setSmartEq(on)
                    if (on) {
                        ensureOn()
                        analysis.request()
                    }
                }
            }
            if (settings.smartEq) {
                val line = when {
                    !s.enabled -> null
                    !smart.playing -> "Aucun son joué sur le téléphone pour l'instant."
                    !analysis.granted || !smart.analyzer ->
                        "Son global détecté : corrections de base. Touchez ici pour analyser le son du téléphone."
                    !smart.hearing -> "Analyse du son global…"
                    else -> "Son global ${smart.character} : l'EQ s'ajuste en direct."
                }
                if (line != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        line,
                        fontSize = 12.sp,
                        color = Lime,
                        modifier = Modifier.clickable(enabled = !analysis.granted) { analysis.request() }
                    )
                }
            }
        }

        // ------------------------------------------------ En lecture
        GlassCard(
            modifier = Modifier.offset(y = (-14).dp),
            pad = 14,
            onClick = {
                if (access) NowPlaying.openPlayer(context) else NowPlaying.openAccessSettings(context)
            }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(CardHi)
                        .border(1.dp, Edge, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (track?.playing == false) "En pause" else "En lecture",
                        fontSize = 12.sp, color = TextLo
                    )
                    Text(
                        when {
                            !access -> "Autoriser l'affichage du titre"
                            track == null -> "Aucune musique détectée"
                            track?.artist.isNullOrBlank() -> track?.title.orEmpty()
                            else -> "${track?.title} · ${track?.artist}"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextHi,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                MiniBars(levels, Modifier.width(72.dp).height(26.dp))
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextHi)
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}
