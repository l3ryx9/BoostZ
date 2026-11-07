package app.boostz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.boostz.K
import app.boostz.tr
import app.boostz.trPreset
import app.boostz.audio.BAND_COUNT
import app.boostz.audio.BAND_LABELS
import app.boostz.audio.EqEngine
import app.boostz.audio.PRESETS

private fun presetIcon(name: String): ImageVector = when (name) {
    "Plat" -> Icons.Filled.Equalizer
    "Pop" -> Icons.Filled.Headphones
    "Rock" -> Icons.Filled.Album
    "Hip-Hop" -> Icons.Filled.Speaker
    "Électronique" -> Icons.Filled.ShowChart
    "Jazz" -> Icons.Filled.LocalBar
    "Classique" -> Icons.Filled.LibraryMusic
    "Vocal" -> Icons.Filled.Mic
    else -> Icons.Filled.Tune
}

private const val CUSTOM_ID = "__custom__"

@Composable
fun EqualizerScreen() {
    val s by EqEngine.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val ensureOn = rememberEnsureOn()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ------------------------------------------------ Égaliseur
        GlassCard(corner = 26, pad = 14) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr(K.TAB_EQ), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = TextHi)
                Spacer(Modifier.width(8.dp))
                LimeSwitch(s.eqOn) {
                    ensureOn()
                    EqEngine.setEqOn(it)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.width(44.dp).height(212.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("+12 dB", fontSize = 10.sp, color = TextLo)
                    Text("0 dB", fontSize = 10.sp, color = TextLo)
                    Text("-12 dB", fontSize = 10.sp, color = TextLo)
                }
                for (i in 0 until BAND_COUNT) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        EqBandSlider(
                            value = s.bands[i],
                            dim = !s.eqOn,
                            modifier = Modifier.width(26.dp).height(212.dp)
                        ) {
                            ensureOn()
                            EqEngine.setBand(i, it)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(BAND_LABELS[i], fontSize = 10.sp, fontWeight = FontWeight.Medium, color = TextHi)
                    }
                }
            }
        }

        // ------------------------------------------------ Préréglages
        GlassCard(corner = 26, pad = 12) {
            SectionTitle(Icons.Filled.GraphicEq, tr(K.PRESETS))
            Spacer(Modifier.height(10.dp))
            val items: List<Pair<String, ImageVector>> =
                listOf(CUSTOM_ID to Icons.Filled.Tune) + PRESETS.map { it.name to presetIcon(it.name) }
            items.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (name, icon) ->
                        val selected = if (name == CUSTOM_ID) s.presetName == null else s.presetName == name
                        ChipButton(icon, if (name == CUSTOM_ID) tr(K.CUSTOM) else trPreset(name), selected, Modifier.weight(1f)) {
                            ensureOn()
                            if (name == CUSTOM_ID) EqEngine.applyCustom()
                            else PRESETS.firstOrNull { it.name == name }?.let { EqEngine.applyPreset(it) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // ------------------------------------------------ Effets audio
        GlassCard(corner = 26, pad = 12) {
            SectionTitle(Icons.Filled.AutoAwesome, tr(K.AUDIO_EFFECTS))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EffectTile(Icons.Filled.SurroundSound, tr(K.WIDE), s.wide, Modifier.weight(1f)) {
                    ensureOn(); EqEngine.setWide(it)
                }
                EffectTile(Icons.Filled.Flare, tr(K.CLARITY), s.clarity, Modifier.weight(1f)) {
                    ensureOn(); EqEngine.setClarity(it)
                }
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun EffectTile(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Boolean) -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(CardHi, Card)), shape)
            .border(1.dp, if (checked) Lime.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.07f), shape)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (checked) Lime else TextHi, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 12.sp, color = TextHi, maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))
        Box { LimeSwitch(checked, onChange = onChange) }
    }
}
