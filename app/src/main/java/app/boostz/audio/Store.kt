package app.boostz.audio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Sauvegarde locale (SharedPreferences) de l'état, des réglages et des profils. */
class Store(context: Context) {
    private val sp = context.getSharedPreferences("boostz", Context.MODE_PRIVATE)

    fun loadState(): EqState {
        val raw = sp.getString("state", null) ?: return EqState()
        return runCatching { eqStateFromJson(JSONObject(raw)) }.getOrDefault(EqState())
    }

    fun saveState(s: EqState) {
        sp.edit().putString("state", s.toJson().toString()).apply()
    }

    fun loadSettings(): Settings {
        val raw = sp.getString("settings", null) ?: return Settings()
        return runCatching { settingsFromJson(JSONObject(raw)) }.getOrDefault(Settings())
    }

    fun saveSettings(s: Settings) {
        sp.edit().putString("settings", s.toJson().toString()).apply()
    }

    fun loadProfiles(): List<Profile> {
        val raw = sp.getString("profiles", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Profile(o.getString("name"), eqStateFromJson(o.getJSONObject("state")))
            }
        }.getOrDefault(emptyList())
    }

    fun saveProfiles(list: List<Profile>) {
        val arr = JSONArray()
        list.forEach { p ->
            val o = JSONObject()
            o.put("name", p.name)
            o.put("state", p.state.toJson())
            arr.put(o)
        }
        sp.edit().putString("profiles", arr.toString()).apply()
    }
}
