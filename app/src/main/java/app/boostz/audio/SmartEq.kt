package app.boostz.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.audiofx.Visualizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.log10

/**
 * EQ intelligent.
 *
 * Analyse le son global qui sort du téléphone (Visualizer sur la session audio 0, c'est-à-dire
 * le mix de toutes les applications — jamais le micro), mesure l'équilibre
 * graves / bas-médium / médium / présence / aigus du morceau, puis ajuste l'égaliseur
 * quand on monte le bass booster ou le loudness :
 *  - plus de basses : on garde le grave propre (moins de « boue » vers 250 Hz, pas
 *    d'excès d'infra si le morceau en est déjà chargé) et la voix reste devant ;
 *  - plus de volume : on évite la dureté des aigus sur un morceau brillant, on ajoute
 *    un peu d'air sur un morceau feutré, et on limite le pompage dans le grave.
 *
 * Sans autorisation d'analyse, des corrections de base (non adaptées au morceau) restent appliquées.
 * Les corrections évoluent lentement pour ne jamais « sauter » à l'oreille.
 */
object SmartEq {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _info = MutableStateFlow(SmartInfo())
    val info: StateFlow<SmartInfo> = _info.asStateFlow()

    /** Niveaux en direct (8 barres, 0..1) pour les petites animations de l'interface. */
    private val _levels = MutableStateFlow(List(8) { 0f })
    val levels: StateFlow<List<Float>> = _levels.asStateFlow()
    private val LEVEL_EDGES = floatArrayOf(40f, 80f, 160f, 320f, 640f, 1280f, 2560f, 5120f, 12000f)

    @Volatile private var retryNow = false

    // Groupes de fréquences analysés : graves, bas-médium, médium, présence, aigus.
    private val GROUP_EDGES = floatArrayOf(20f, 150f, 500f, 2000f, 6000f, 16000f)
    private val GROUP_OCTAVES = FloatArray(5) { i -> (log10(GROUP_EDGES[i + 1] / GROUP_EDGES[i]) / log10(2f)) }
    // Équilibre « typique » d'un morceau de musique (dB relatifs), servant de référence.
    private val REFERENCE = floatArrayOf(4f, 2f, 0f, -3f, -7f)

    private val profile = FloatArray(5)
    private var hasProfile = false
    private val current = FloatArray(BAND_COUNT)
    private var fft = ByteArray(0)

    /** Démarre ou arrête la boucle selon que le moteur et l'option sont actifs. */
    @Synchronized
    fun update(context: Context, run: Boolean) {
        if (run && job == null) {
            start(context.applicationContext)
        } else if (!run && job != null) {
            job?.cancel()
            job = null
            for (i in current.indices) current[i] = 0f
            _info.value = SmartInfo()
            _levels.value = List(8) { 0f }
            EqEngine.requestApply()
        }
    }

    /** À appeler quand l'utilisateur vient d'accorder l'autorisation d'analyse. */
    fun retry() {
        retryNow = true
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun start(ctx: Context) {
        hasProfile = false
        for (i in profile.indices) profile[i] = 0f
        job = scope.launch {
            var vis: Visualizer? = openVisualizer(ctx)
            val audio = ctx.getSystemService(AudioManager::class.java)
            var tick = 0
            var hearing = false
            try {
                while (isActive) {
                    if (vis == null && (retryNow || tick % 60 == 0)) {
                        retryNow = false
                        vis = openVisualizer(ctx)
                    }
                    val v = vis
                    val playing = runCatching { audio?.isMusicActive == true }.getOrDefault(false)
                    hearing = if (v != null) analyze(v) && playing else false
                    if (tick % 3 == 0) recompute(analyzer = v != null, hearing = hearing, playing = playing)
                    tick++
                    delay(150)
                }
            } finally {
                vis?.let {
                    runCatching { it.setEnabled(false) }
                    runCatching { it.release() }
                }
            }
        }
    }

    private fun openVisualizer(ctx: Context): Visualizer? {
        if (!hasPermission(ctx)) return null
        return runCatching {
            val v = Visualizer(0)
            v.setEnabled(false)
            val range = Visualizer.getCaptureSizeRange()
            v.setCaptureSize(minOf(range[1], 1024))
            v.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED)
            v.setEnabled(true)
            v
        }.getOrNull()
    }

    /** Lit une FFT et met à jour le profil spectral lissé. Renvoie false s'il n'y a pas de son. */
    private fun analyze(v: Visualizer): Boolean {
        val n = runCatching { v.captureSize }.getOrDefault(0)
        if (n <= 0) return false
        if (fft.size != n) fft = ByteArray(n)
        if (runCatching { v.getFft(fft) }.getOrDefault(Visualizer.ERROR) != Visualizer.SUCCESS) return false
        val sampleRate = v.samplingRate / 1000f
        if (sampleRate <= 0f) return false

        val energy = FloatArray(5)
        val levelEnergy = FloatArray(8)
        val levelCount = IntArray(8)
        var total = 0f
        for (k in 1 until n / 2) {
            val re = fft[2 * k].toFloat()
            val im = fft[2 * k + 1].toFloat()
            val p = re * re + im * im
            val f = k * sampleRate / n
            val group = groupOf(f)
            if (group >= 0) {
                energy[group] += p
                total += p
            }
            for (j in 0 until 8) {
                if (f >= LEVEL_EDGES[j] && f < LEVEL_EDGES[j + 1]) {
                    levelEnergy[j] += p
                    levelCount[j]++
                    break
                }
            }
        }
        if (total < 40f) {   // silence ou presque
            _levels.value = _levels.value.map { it * 0.7f }
            return false
        }
        val previous = _levels.value
        _levels.value = List(8) { j ->
            val e = levelEnergy[j] / maxOf(levelCount[j], 1)
            val lv = ((10f * log10(e + 1f) - 5f) / 35f).coerceIn(0f, 1f)
            maxOf(lv, previous.getOrElse(j) { 0f } * 0.75f)
        }

        val db = FloatArray(5) { i -> 10f * log10(energy[i] / GROUP_OCTAVES[i] + 1e-3f) }
        val mean = db.average().toFloat()
        for (i in 0 until 5) {
            val rel = db[i] - mean
            profile[i] = if (hasProfile) profile[i] * 0.94f + rel * 0.06f else rel
        }
        hasProfile = true
        return true
    }

    private fun groupOf(f: Float): Int {
        for (i in 0 until 5) if (f >= GROUP_EDGES[i] && f < GROUP_EDGES[i + 1]) return i
        return -1
    }

    /** Écart du morceau par rapport à la référence, centré, en dB (graves, bas-méd., méd., présence, aigus). */
    private fun deviation(): FloatArray {
        if (!hasProfile) return FloatArray(5)
        val d = FloatArray(5) { i -> profile[i] - REFERENCE[i] }
        val mean = d.average().toFloat()
        for (i in d.indices) d[i] = (d[i] - mean).coerceIn(-9f, 9f)
        return d
    }

    private fun describe(d: FloatArray, hearing: Boolean): String {
        if (!hasProfile) return ""
        if (!hearing) return "en attente de musique"
        val candidates = listOf(
            d[0] to "riche en basses",
            -d[0] to "léger en basses",
            (d[2] + d[3]) / 2f to "centré sur les voix",
            (d[3] + d[4]) / 2f to "brillant et aigu",
            -d[4] to "feutré, peu d'aigus"
        )
        val best = candidates.maxByOrNull { it.first }
        return if (best != null && best.first > 2.5f) best.second else "équilibré"
    }

    private fun recompute(analyzer: Boolean, hearing: Boolean, playing: Boolean) {
        val s = EqEngine.state.value
        val b = s.bass / 1000f
        val v = s.loudness / 100f
        val d = deviation()
        fun pos(x: Float) = (x / 6f).coerceIn(0f, 1f)

        val bassHeavy = pos(d[0])
        val bassLight = pos(-d[0])
        val muddy = pos(d[1])
        val dullPresence = pos(-d[3])
        val harsh = pos(d[3])
        val bright = pos(d[4])
        val dark = pos(-d[4])

        // Index des bandes : 0=31 1=62 2=125 3=250 4=500 5=1k 6=2k 7=4k 8=8k 9=16k
        val t = FloatArray(BAND_COUNT)
        // --- Bass booster
        t[0] += b * (-2.5f * bassHeavy)                 // morceau déjà chargé : pas de boum dans l'infra
        t[2] += b * (2.5f * bassLight)                  // morceau léger : du corps à 125 Hz
        t[3] += b * (-1.5f - 1.5f * muddy)              // grave propre : on dégraisse le bas-médium
        t[4] += b * (-0.5f * muddy)
        t[6] += b * (0.5f + 0.5f * dullPresence)
        t[7] += b * (1.5f + 1.0f * dullPresence)        // la voix reste devant les basses
        // --- Loudness
        t[1] += v * (-0.5f - 1.5f * bassHeavy)          // moins de pompage du limiteur
        t[5] += v * 0.5f                                // intelligibilité
        t[6] += v * (-1.5f * harsh)                     // pas d'agressivité vers 2 kHz
        t[8] += v * (-2.0f * bright)                    // morceau brillant : pas de dureté
        t[9] += v * (2.0f * dark - 1.0f * bright)       // morceau feutré : un peu d'air

        var changed = false
        for (i in 0 until BAND_COUNT) {
            val target = t[i].coerceIn(-6f, 6f)
            val step = (target - current[i]).coerceIn(-0.6f, 0.6f)
            if (abs(step) > 0.01f) {
                current[i] += step
                changed = true
            }
        }

        _info.value = SmartInfo(
            running = true,
            analyzer = analyzer,
            hearing = hearing,
            playing = playing,
            character = describe(d, hearing),
            offsets = current.toList()
        )
        if (changed) EqEngine.requestApply()
    }
}
