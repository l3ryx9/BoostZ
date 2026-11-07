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
import app.boostz.K
import app.boostz.tr
import app.boostz.audio.BASS_MAX_DB
import app.boostz.audio.EqEngine
import app.boostz.audio.LOUDNESS_MAX_DB
import app.boostz.audio.NowPlaying
import kotlin.math.roundToInt

private fun dbText(db: Float): String =
    if (db < 0.05f) "0 dB" else "+" + String.format("%.1f", db).removeSuffix(",0").removeSuffix(".0") + " dB"

private val MINI_LEVELS = listOf(0.35f, 0.65f, 0.5f, 0.9f, 0.6f, 0.8f, 0.45f, 0.7f)

@Composable
fun MainScreen() {
    val s by EqEngine.state.collectAsStateWithLifecycle()
    val levels = emptyList<Float>()
    val track by NowPlaying.track.collectAsStateWithLifecycle()
    val access by NowPlaying.access.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val ensureOn = rememberEnsureOn()


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
                    title = tr(K.VOLUME_BOOSTER),
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
                    title = tr(K.BASS_BOOSTER),
                    valueText = dbText(s.bass / 1000f * BASS_MAX_DB),
                    active = s.enabled,
                    modifier = Modifier.fillMaxWidth(0.68f).offset(y = (-18).dp)
                ) {
                    ensureOn()
                    EqEngine.setBass((it * 1000f).roundToInt())
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
                        if (track?.playing == false) tr(K.PAUSED) else tr(K.NOW_PLAYING),
                        fontSize = 12.sp, color = TextLo
                    )
                    Text(
                        when {
                            !access -> tr(K.ALLOW_TITLE)
                            track == null -> tr(K.NO_MUSIC)
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
                MiniBars(MINI_LEVELS, Modifier.width(72.dp).height(26.dp))
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextHi)
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}
