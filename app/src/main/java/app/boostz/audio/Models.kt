package app.boostz.audio

import org.json.JSONArray
import org.json.JSONObject

const val BAND_COUNT = 10
const val MAX_DB = 12f

/** Loudness : 0..100 % => 0..30 dB (post-gain du limiteur, comme l'original). */
const val LOUDNESS_MAX_DB = 30f

/** Clarté : légère mise en avant des voix et des détails. */
val CLARITY_CURVE = listOf(0f, 0f, 0f, -1.5f, -1f, 0f, 2.5f, 4f, 3.5f, 2f)

/** Stéréo large : écart de timbre entre gauche et droite au-dessus de 500 Hz (le grave reste centré). */
const val WIDE_DB = 5f

/** Bass booster : 0..1000 => 0..15 dB sous 80 Hz (pré-égaliseur, comme l'original). */
const val BASS_MAX_DB = 15f
const val BASS_FREQ_HZ = 80f

val BAND_CENTERS = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
val BAND_LABELS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

/** Limites hautes de chaque bande pour DynamicsProcessing (moyenne géométrique entre centres). */
val BAND_EDGES = floatArrayOf(44f, 88f, 177f, 354f, 707f, 1414f, 2828f, 5657f, 11314f, 20000f)

/** L'index correspond aux constantes PresetReverb (NONE = 0 ... PLATE = 6). */
val REVERB_NAMES = listOf(
    "Aucune", "Petite salle", "Salle moyenne", "Grande salle",
    "Hall moyen", "Grand hall", "Plaque"
)

data class EqState(
    val enabled: Boolean = false,
    val bands: List<Float> = List(BAND_COUNT) { 0f },
    val bass: Int = 0,          // 0..1000
    val loudness: Int = 0,      // 0..100 %
    val eqOn: Boolean = true,
    val wide: Boolean = false,
    val clarity: Boolean = false,
    val customBands: List<Float> = List(BAND_COUNT) { 0f },
    val reverb: Int = 0,        // index dans REVERB_NAMES
    val balance: Float = 0f,    // -1 (gauche) .. +1 (droite)
    val limiter: Boolean = true,
    val presetName: String? = null      // null = Personnalisé
)

data class Settings(
    val startOnBoot: Boolean = true,
    val lang: String = ""       // "" = langue du téléphone
)

data class Profile(val name: String, val state: EqState)

data class Preset(val name: String, val gains: List<Float>)

data class EngineStatus(
    val attached: Boolean = false,
    val effects: List<Pair<String, Boolean>> = emptyList()
)

private fun g(vararg v: Int): List<Float> = v.map { it.toFloat() }

val PRESETS = listOf(
    Preset("Plat", g(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
    Preset("Pop", g(-1, 1, 3, 4, 3, 0, -1, -1, 1, 2)),
    Preset("Rock", g(5, 4, 3, 1, -1, -1, 2, 3, 4, 5)),
    Preset("Hip-Hop", g(6, 5, 2, 3, -1, -1, 2, 0, 2, 3)),
    Preset("Électronique", g(6, 5, 2, 0, -2, 2, 1, 2, 5, 6)),
    Preset("Jazz", g(3, 2, 1, 2, -2, -2, 0, 1, 2, 3)),
    Preset("Classique", g(4, 3, 2, 1, -1, -1, 0, 2, 3, 4)),
    Preset("Vocal", g(-3, -2, -1, 1, 4, 4, 3, 1, 0, -1))
)

// ---------------------------------------------------------------- JSON

fun EqState.toJson(): JSONObject {
    val o = JSONObject()
    o.put("enabled", enabled)
    val arr = JSONArray()
    bands.forEach { arr.put(it.toDouble()) }
    o.put("bands", arr)
    o.put("bass", bass)
    o.put("loud", loudness)
    o.put("eqOn", eqOn)
    o.put("wide", wide)
    o.put("clar", clarity)
    val custom = JSONArray()
    customBands.forEach { custom.put(it.toDouble()) }
    o.put("custom", custom)
    o.put("rev", reverb)
    o.put("bal", balance.toDouble())
    o.put("lim", limiter)
    o.put("preset", presetName ?: "")
    return o
}

fun eqStateFromJson(o: JSONObject): EqState {
    val arr = o.optJSONArray("bands")
    val bands = List(BAND_COUNT) { i ->
        (arr?.optDouble(i, 0.0) ?: 0.0).toFloat().coerceIn(-MAX_DB, MAX_DB)
    }
    val preset = o.optString("preset", "")
    val customArr = o.optJSONArray("custom")
    val custom = List(BAND_COUNT) { i ->
        (customArr?.optDouble(i, 0.0) ?: 0.0).toFloat().coerceIn(-MAX_DB, MAX_DB)
    }
    return EqState(
        enabled = o.optBoolean("enabled", false),
        bands = bands,
        bass = o.optInt("bass", 0).coerceIn(0, 1000),
        loudness = o.optInt("loud", 0).coerceIn(0, 100),
        eqOn = o.optBoolean("eqOn", true),
        wide = o.optBoolean("wide", false),
        clarity = o.optBoolean("clar", false),
        customBands = custom,
        reverb = o.optInt("rev", 0).coerceIn(0, REVERB_NAMES.lastIndex),
        balance = o.optDouble("bal", 0.0).toFloat().coerceIn(-1f, 1f),
        limiter = o.optBoolean("lim", true),
        presetName = if (preset.isEmpty()) null else preset
    )
}

fun Settings.toJson(): JSONObject {
    val o = JSONObject()
    o.put("boot", startOnBoot)
    o.put("lang", lang)
    return o
}

fun settingsFromJson(o: JSONObject): Settings = Settings(
    startOnBoot = o.optBoolean("boot", true),
    lang = o.optString("lang", "")
)
