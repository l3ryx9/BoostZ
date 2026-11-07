package app.boostz.audio

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import app.boostz.service.EqService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.ln

/**
 * Moteur audio global : tous les effets sont attachés à la session audio 0,
 * c'est-à-dire au mix de sortie du téléphone (toutes les applications).
 *
 * Chaîne DynamicsProcessing (Android 9+), identique à l'application d'origine :
 *   pré-égaliseur 1 bande (bass booster, < 80 Hz, jusqu'à +15 dB)
 *   → post-égaliseur 10 bandes (vos réglages + corrections de l'EQ intelligent)
 *   → limiteur par canal (post-gain = équilibre G/D + loudness jusqu'à +30 dB)
 */
object EqEngine {
    private lateinit var store: Store
    private lateinit var appContext: Context
    private var ready = false

    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val trigger = MutableSharedFlow<Unit>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val _state = MutableStateFlow(EqState())
    val state: StateFlow<EqState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _status = MutableStateFlow(EngineStatus())
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    private var fx: GlobalFx? = null
    private var savedState: EqState? = null
    private var savedSettings: Settings? = null

    // ------------------------------------------------------------ cycle de vie

    @Synchronized
    fun init(context: Context) {
        if (ready) return
        ready = true
        appContext = context.applicationContext
        store = Store(appContext)
        _state.value = store.loadState()
        _settings.value = store.loadSettings()
        _profiles.value = store.loadProfiles()
        savedState = _state.value
        savedSettings = _settings.value
        scope.launch {
            trigger.collectLatest {
                delay(30)
                persist()
                reconcile()
            }
        }
        requestApply()
    }

    /** Demande une nouvelle application des effets (anti-rebond de 30 ms). */
    fun requestApply() {
        trigger.tryEmit(Unit)
    }

    private fun persist() {
        val s = _state.value
        if (s != savedState) {
            store.saveState(s)
            savedState = s
        }
        val st = _settings.value
        if (st != savedSettings) {
            store.saveSettings(st)
            savedSettings = st
        }
    }

    /** Allume ou éteint le moteur et gère le service de premier plan. */
    fun setPower(context: Context, on: Boolean) {
        setEnabled(on)
        val app = context.applicationContext
        val intent = Intent(app, EqService::class.java)
        if (on) {
            runCatching { ContextCompat.startForegroundService(app, intent) }
        } else {
            runCatching { app.stopService(intent) }
        }
    }

    fun setEnabled(on: Boolean) = change { it.copy(enabled = on) }

    /** Détruit puis recrée les effets (utile si une autre application a pris la main). */
    fun rebuild() {
        synchronized(lock) {
            fx?.release()
            fx = null
        }
        requestApply()
    }

    // ------------------------------------------------------------ état audio

    private fun change(block: (EqState) -> EqState) {
        _state.update(block)
        requestApply()
    }

    fun setBand(index: Int, db: Float) = change { s ->
        val list = s.bands.toMutableList()
        list[index] = db.coerceIn(-MAX_DB, MAX_DB)
        s.copy(bands = list, customBands = list, presetName = null)
    }

    /** Revient au réglage « Personnalisé » (dernière courbe faite à la main). */
    fun applyCustom() = change { it.copy(bands = it.customBands, presetName = null) }

    fun setEqOn(on: Boolean) = change { it.copy(eqOn = on) }
    fun setWide(on: Boolean) = change { it.copy(wide = on) }
    fun setClarity(on: Boolean) = change { it.copy(clarity = on) }

    fun applyPreset(p: Preset) = change { it.copy(bands = p.gains, presetName = p.name) }

    fun resetBands() = change {
        it.copy(bands = List(BAND_COUNT) { 0f }, presetName = "Plat")
    }

    fun setBass(v: Int) = change { it.copy(bass = v.coerceIn(0, 1000)) }
    fun setLoudness(v: Int) = change { it.copy(loudness = v.coerceIn(0, 100)) }
    fun setReverb(v: Int) = change { it.copy(reverb = v.coerceIn(0, REVERB_NAMES.lastIndex)) }
    fun setBalance(v: Float) = change { it.copy(balance = v.coerceIn(-1f, 1f)) }
    fun setLimiter(on: Boolean) = change { it.copy(limiter = on) }

    // ------------------------------------------------------------ réglages

    fun setStartOnBoot(on: Boolean) {
        _settings.update { it.copy(startOnBoot = on) }
        requestApply()
    }

    fun setSmartEq(on: Boolean) {
        _settings.update { it.copy(smartEq = on) }
        requestApply()
    }

    // ------------------------------------------------------------ profils

    fun saveProfile(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        val snapshot = _state.value.copy(enabled = false, presetName = null)
        _profiles.update { list -> list.filter { it.name != n } + Profile(n, snapshot) }
        store.saveProfiles(_profiles.value)
    }

    fun applyProfile(p: Profile) = change { cur ->
        p.state.copy(enabled = cur.enabled, presetName = null)
    }

    fun deleteProfile(name: String) {
        _profiles.update { list -> list.filter { it.name != name } }
        store.saveProfiles(_profiles.value)
    }

    // ------------------------------------------------------------ application

    private fun reconcile() {
        val s = _state.value
        synchronized(lock) {
            if (!s.enabled) {
                fx?.release()
                fx = null
            } else {
                val f = fx ?: GlobalFx().also { fx = it }
                f.applyState(s, SmartEq.info.value.offsets)
            }
            publishStatus()
        }
        SmartEq.update(appContext, s.enabled && _settings.value.smartEq)
    }

    private fun publishStatus() {
        val f = fx
        if (f == null) {
            _status.value = EngineStatus()
            return
        }
        val dp = f.dp != null
        _status.value = EngineStatus(
            attached = dp || f.eq != null,
            effects = listOf(
                (if (dp) "Égaliseur 10 bandes" else "Égaliseur système") to (dp || f.eq != null),
                "Bass booster" to (dp || f.bass != null),
                "Loudness" to (dp || f.loud != null),
                "Stéréo large" to dp,
                "Limiteur anti-saturation" to dp,
                "Réverbération" to (f.rev != null)
            )
        )
    }

    // ------------------------------------------------------------ effets globaux (session 0)

    private class GlobalFx {
        var dp: AudioEffect? = null
        var eq: Equalizer? = null       // repli sans DynamicsProcessing
        var bass: BassBoost? = null     // repli sans DynamicsProcessing
        var loud: LoudnessEnhancer? = null  // repli sans DynamicsProcessing
        var rev: PresetReverb? = null
        private var created = false

        fun applyState(s: EqState, auto: List<Float>) {
            if (!created) {
                created = true
                create()
            }
            val bassDb = s.bass / 1000f * BASS_MAX_DB
            val loudDb = s.loudness / 100f * LOUDNESS_MAX_DB
            val bands = List(BAND_COUNT) { i ->
                val user = if (s.eqOn) s.bands[i] else 0f
                val clarity = if (s.clarity) CLARITY_CURVE[i] else 0f
                (user + clarity + auto.getOrElse(i) { 0f }).coerceIn(-15f, 15f)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dp?.let { d -> runCatching { applyDp(d as DynamicsProcessing, s, bands, bassDb, loudDb) } }
            }
            eq?.let { e -> runCatching { applyEqualizer(e, bands) } }
            bass?.let { b ->
                runCatching {
                    if (b.strengthSupported) b.setStrength(s.bass.toShort())
                    b.setEnabled(s.bass > 0)
                }
            }
            loud?.let { l ->
                runCatching {
                    val gainMb = (loudDb * 100f).toInt()
                    l.setTargetGain(gainMb)
                    l.setEnabled(gainMb > 0)
                }
            }
            rev?.let { r ->
                runCatching {
                    r.setPreset(s.reverb.toShort())
                    r.setEnabled(s.reverb > 0)
                }
            }
        }

        private fun create() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dp = runCatching { buildDp() }.getOrNull()
            }
            if (dp == null) {
                // Sans DynamicsProcessing : effets système classiques (même priorité que l'original).
                eq = runCatching {
                    val e = Equalizer(PRIORITY, 0)
                    e.setEnabled(true)
                    e
                }.getOrNull()
                bass = runCatching { BassBoost(PRIORITY, 0) }.getOrNull()
                loud = runCatching { LoudnessEnhancer(0) }.getOrNull()
            }
            rev = runCatching { PresetReverb(PRIORITY, 0) }.getOrNull()
        }

        @RequiresApi(Build.VERSION_CODES.P)
        private fun buildDp(): AudioEffect {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                true, 1,            // pré-égaliseur : 1 bande = bass booster
                false, 0,           // compresseur multibande (inutilisé)
                true, BAND_COUNT,   // post-égaliseur : 10 bandes
                true                // limiteur
            ).build()
            val d = DynamicsProcessing(PRIORITY, 0, config)
            d.setEnabled(true)
            return d
        }

        @RequiresApi(Build.VERSION_CODES.P)
        private fun applyDp(
            d: DynamicsProcessing,
            s: EqState,
            bands: List<Float>,
            bassDb: Float,
            loudDb: Float
        ) {
            // Bass booster : gain ajouté sous 80 Hz, sans rien retirer au reste du spectre.
            val pre = DynamicsProcessing.Eq(true, true, 1)
            val bassBand = pre.getBand(0)
            bassBand.setCutoffFrequency(BASS_FREQ_HZ)
            bassBand.setGain(bassDb)
            bassBand.setEnabled(true)
            d.setPreEqAllChannelsTo(pre)

            // Post-égaliseur, canal par canal (Stéréo large décale légèrement le timbre G / D).
            for (ch in 0..1) {
                val post = DynamicsProcessing.Eq(true, true, BAND_COUNT)
                for (i in 0 until BAND_COUNT) {
                    val widen = if (s.wide && i >= 4) {
                        val sign = if ((i % 2 == 0) == (ch == 0)) 1f else -1f
                        sign * WIDE_DB
                    } else 0f
                    val band = post.getBand(i)
                    band.setCutoffFrequency(BAND_EDGES[i])
                    band.setGain(bands[i] + widen)
                    band.setEnabled(true)
                }
                d.setPostEqByChannelIndex(ch, post)
            }

            // Loudness + équilibre : post-gain du limiteur de chaque canal.
            d.setLimiterByChannelIndex(0, limiterFor(s, channelGainDb(s.balance, true) + loudDb))
            d.setLimiterByChannelIndex(1, limiterFor(s, channelGainDb(s.balance, false) + loudDb))
        }

        /** Réglages par défaut du limiteur Android (1 ms / 60 ms / 10:1 / -2 dB), comme l'original. */
        @RequiresApi(Build.VERSION_CODES.P)
        private fun limiterFor(s: EqState, postGainDb: Float): DynamicsProcessing.Limiter =
            DynamicsProcessing.Limiter(
                true, true, 0, 1f, 60f,
                if (s.limiter) 10f else 1f,
                if (s.limiter) -2f else 0f,
                postGainDb
            )

        private fun applyEqualizer(e: Equalizer, bands: List<Float>) {
            val count = e.numberOfBands.toInt()
            val range = e.bandLevelRange
            for (b in 0 until count) {
                val centerHz = e.getCenterFreq(b.toShort()) / 1000f
                val db = interpolate(bands, centerHz)
                val level = (db * 100f).toInt().coerceIn(range[0].toInt(), range[1].toInt())
                e.setBandLevel(b.toShort(), level.toShort())
            }
            e.setEnabled(true)
        }

        fun release() {
            listOf<AudioEffect?>(dp, eq, bass, loud, rev).forEach { effect ->
                runCatching { effect?.setEnabled(false) }
                runCatching { effect?.release() }
            }
            dp = null; eq = null; bass = null; loud = null; rev = null
        }
    }

    // ------------------------------------------------------------ utilitaires

    private const val PRIORITY = Int.MAX_VALUE

    /** Équilibre -1..1 : on atténue le côté opposé jusqu'à -20 dB, comme l'original. */
    private fun channelGainDb(balance: Float, left: Boolean): Float {
        val attenuation = if (left) balance.coerceAtLeast(0f) else (-balance).coerceAtLeast(0f)
        return -20f * attenuation
    }

    /** Interpolation logarithmique de la courbe 10 bandes à une fréquence donnée. */
    private fun interpolate(bands: List<Float>, hz: Float): Float {
        if (hz <= BAND_CENTERS.first()) return bands.first()
        if (hz >= BAND_CENTERS.last()) return bands.last()
        for (i in 0 until BAND_COUNT - 1) {
            val a = BAND_CENTERS[i]
            val b = BAND_CENTERS[i + 1]
            if (hz in a..b) {
                val t = ln(hz / a) / ln(b / a)
                return bands[i] + (bands[i + 1] - bands[i]) * t
            }
        }
        return 0f
    }
}
