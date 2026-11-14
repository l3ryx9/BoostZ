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
 * Moteur audio : par défaut les effets sont attachés à la session audio 0 (mix de sortie global).
 * Dès qu'un lecteur annonce sa session (ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION), les effets
 * sont attachés à ces sessions-là à la place : c'est la méthode qui marche sur Android 10+,
 * où la session 0 est ignorée par beaucoup d'appareils. Sans session annoncée, repli sur la session 0.
 *
 * Chaîne DynamicsProcessing (Android 9+), identique à l'application d'origine :
 *   pré-égaliseur 1 bande (bass booster, < 80 Hz, jusqu'à +15 dB)
 *   → post-égaliseur 10 bandes (vos réglages)
 *   → limiteur par canal (post-gain = équilibre G/D + loudness jusqu'à +30 dB)
 */
object EqEngine {
    private lateinit var store: Store
    private lateinit var appContext: Context
    private var ready = false

    private val lock = Any()
    private val persistLock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val trigger = MutableSharedFlow<Unit>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Sauvegarde différée : pas d'écriture disque à chaque mouvement de curseur. */
    private val persistTrigger = MutableSharedFlow<Unit>(
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

    /** Sessions annoncées par les lecteurs, et un jeu d'effets par session ciblée (sous [lock]). */
    private val sessions = LinkedHashSet<Int>()
    private val fxMap = HashMap<Int, GlobalFx>()
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
                reconcile()
            }
        }
        scope.launch {
            persistTrigger.collectLatest {
                delay(PERSIST_DELAY_MS)
                persist()
            }
        }
        requestApply()
    }

    /** Demande une nouvelle application des effets (anti-rebond de 30 ms). */
    fun requestApply() {
        trigger.tryEmit(Unit)
    }

    private fun requestPersist() {
        persistTrigger.tryEmit(Unit)
    }

    private fun persist() {
        synchronized(persistLock) {
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
    }

    /** Écrit tout de suite ce qui est en attente (à appeler quand l'application quitte l'écran). */
    fun flush() {
        if (ready) persist()
    }

    fun markIntroSeen() {
        _settings.update { it.copy(introSeen = true) }
        requestPersist()
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

    /** Démarre le service de premier plan seulement s'il ne tourne pas déjà. */
    fun ensureService(context: Context) {
        if (EqService.running) return
        val app = context.applicationContext
        runCatching { ContextCompat.startForegroundService(app, Intent(app, EqService::class.java)) }
    }

    /** Détruit puis recrée les effets (utile si une autre application a pris la main). */
    fun rebuild() {
        synchronized(lock) { releaseAll() }
        requestApply()
    }

    // ------------------------------------------------------------ sessions des lecteurs

    /** Un lecteur annonce sa session audio : on y attache les effets. */
    fun openSession(id: Int) {
        if (id <= 0) return
        val added = synchronized(lock) { sessions.add(id) }
        if (added) requestApply()
    }

    /** Un lecteur ferme sa session audio : on libère les effets qui y étaient attachés. */
    fun closeSession(id: Int) {
        val removed = synchronized(lock) { sessions.remove(id) }
        if (removed) requestApply()
    }

    private fun releaseAll() {
        fxMap.values.forEach { it.release() }
        fxMap.clear()
    }

    // ------------------------------------------------------------ état audio

    private fun change(block: (EqState) -> EqState) {
        _state.update(block)
        requestApply()
        requestPersist()
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

    fun setLanguage(code: String) {
        _settings.update { it.copy(lang = code) }
        requestPersist()
    }

    fun setStartOnBoot(on: Boolean) {
        _settings.update { it.copy(startOnBoot = on) }
        requestPersist()
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
                releaseAll()
            } else {
                val targets: Set<Int> = if (sessions.isEmpty()) setOf(GLOBAL_SESSION) else sessions.toSet()
                val iter = fxMap.entries.iterator()
                while (iter.hasNext()) {
                    val e = iter.next()
                    if (e.key !in targets) {
                        e.value.release()
                        iter.remove()
                    }
                }
                for (id in targets) {
                    fxMap.getOrPut(id) { GlobalFx(id) }.applyState(s)
                }
            }
            publishStatus()
        }
    }

    private fun publishStatus() {
        val f = fxMap.values.firstOrNull()
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

    private class GlobalFx(private val session: Int) {
        var dp: AudioEffect? = null
        var eq: Equalizer? = null       // repli sans DynamicsProcessing
        var bass: BassBoost? = null     // repli sans DynamicsProcessing
        var loud: LoudnessEnhancer? = null  // repli sans DynamicsProcessing
        var rev: PresetReverb? = null
        private var created = false

        // Dernières valeurs envoyées : on ne rappelle le système que pour ce qui a changé.
        private var lastBassDb: Float? = null
        private val lastPost = arrayOfNulls<FloatArray>(2)
        private val lastLimiter = arrayOfNulls<Pair<Float, Boolean>>(2)
        private var lastEqBands: List<Float>? = null
        private var lastBassStrength = -1
        private var lastLoudMb = Int.MIN_VALUE
        private var lastReverb = -1

        fun applyState(s: EqState) {
            if (!created) {
                created = true
                create()
            }
            val bassDb = s.bass / 1000f * BASS_MAX_DB
            val loudDb = s.loudness / 100f * LOUDNESS_MAX_DB
            val bands = List(BAND_COUNT) { i ->
                val user = if (s.eqOn) s.bands[i] else 0f
                val clarity = if (s.clarity) CLARITY_CURVE[i] else 0f
                (user + clarity).coerceIn(-15f, 15f)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dp?.let { d -> runCatching { applyDp(d as DynamicsProcessing, s, bands, bassDb, loudDb) } }
            }
            eq?.let { e ->
                runCatching {
                    if (lastEqBands != bands) {
                        applyEqualizer(e, bands)
                        lastEqBands = bands
                    }
                }
            }
            bass?.let { b ->
                runCatching {
                    if (lastBassStrength != s.bass) {
                        if (b.strengthSupported) b.setStrength(s.bass.toShort())
                        b.setEnabled(s.bass > 0)
                        lastBassStrength = s.bass
                    }
                }
            }
            loud?.let { l ->
                runCatching {
                    val gainMb = (loudDb * 100f).toInt()
                    if (lastLoudMb != gainMb) {
                        l.setTargetGain(gainMb)
                        l.setEnabled(gainMb > 0)
                        lastLoudMb = gainMb
                    }
                }
            }
            rev?.let { r ->
                runCatching {
                    if (lastReverb != s.reverb) {
                        r.setPreset(s.reverb.toShort())
                        r.setEnabled(s.reverb > 0)
                        lastReverb = s.reverb
                    }
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
                    val e = Equalizer(PRIORITY, session)
                    e.setEnabled(true)
                    e
                }.getOrNull()
                bass = runCatching { BassBoost(PRIORITY, session) }.getOrNull()
                loud = runCatching { LoudnessEnhancer(session) }.getOrNull()
            }
            rev = runCatching { PresetReverb(PRIORITY, session) }.getOrNull()
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
            val d = DynamicsProcessing(PRIORITY, session, config)
            d.setEnabled(true)
            return d
        }

        /** Stéréo large : décale légèrement le timbre gauche / droite au-dessus de 500 Hz. */
        private fun widenDb(s: EqState, band: Int, channel: Int): Float {
            if (!s.wide || band < 4) return 0f
            val sign = if ((band % 2 == 0) == (channel == 0)) 1f else -1f
            return sign * WIDE_DB
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
            if (lastBassDb != bassDb) {
                if (lastBassDb == null) {
                    val pre = DynamicsProcessing.Eq(true, true, 1)
                    val bassBand = pre.getBand(0)
                    bassBand.setCutoffFrequency(BASS_FREQ_HZ)
                    bassBand.setGain(bassDb)
                    bassBand.setEnabled(true)
                    d.setPreEqAllChannelsTo(pre)
                } else {
                    d.setPreEqBandAllChannelsTo(0, DynamicsProcessing.EqBand(true, BASS_FREQ_HZ, bassDb))
                }
                lastBassDb = bassDb
            }

            // Post-égaliseur, canal par canal : première fois en entier, ensuite seules les bandes modifiées.
            for (ch in 0..1) {
                val target = FloatArray(BAND_COUNT) { i -> bands[i] + widenDb(s, i, ch) }
                val prev = lastPost[ch]
                if (prev == null) {
                    val post = DynamicsProcessing.Eq(true, true, BAND_COUNT)
                    for (i in 0 until BAND_COUNT) {
                        val band = post.getBand(i)
                        band.setCutoffFrequency(BAND_EDGES[i])
                        band.setGain(target[i])
                        band.setEnabled(true)
                    }
                    d.setPostEqByChannelIndex(ch, post)
                } else {
                    for (i in 0 until BAND_COUNT) {
                        if (prev[i] != target[i]) {
                            d.setPostEqBandByChannelIndex(
                                ch, i, DynamicsProcessing.EqBand(true, BAND_EDGES[i], target[i])
                            )
                        }
                    }
                }
                lastPost[ch] = target
            }

            // Loudness + équilibre : post-gain du limiteur de chaque canal.
            for (ch in 0..1) {
                val key = Pair(channelGainDb(s.balance, ch == 0) + loudDb, s.limiter)
                if (lastLimiter[ch] != key) {
                    d.setLimiterByChannelIndex(ch, limiterFor(s, key.first))
                    lastLimiter[ch] = key
                }
            }
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
    private const val GLOBAL_SESSION = 0
    private const val PERSIST_DELAY_MS = 500L

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
