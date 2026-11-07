package app.boostz

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.boostz.audio.EqEngine
import java.util.Locale

/** Langues disponibles dans l'application. */
enum class Lang(val code: String, val label: String) {
    FR("fr", "Français"),
    EN("en", "English"),
    ES("es", "Español"),
    RU("ru", "Русский"),
    ZH("zh", "中文"),
    IT("it", "Italiano")
}

/** Clés de texte. L'ordre des traductions : FR, EN, ES, RU, ZH, IT. */
enum class K {
    TAB_MAIN, TAB_EQ,
    MENU_STOP, MENU_START, MENU_BOOT, MENU_RESTART, MENU_LANGUAGE,
    VOLUME_BOOSTER, BASS_BOOSTER,
    NOW_PLAYING, PAUSED, ALLOW_TITLE, NO_MUSIC,
    PRESETS, CUSTOM, AUDIO_EFFECTS, WIDE, CLARITY,
    P_FLAT, P_POP, P_ROCK, P_HIPHOP, P_ELECTRONIC, P_JAZZ, P_CLASSICAL, P_VOCAL,
    NOTIF_CHANNEL, NOTIF_TEXT, NOTIF_STOP
}

object I18n {
    private val T: Map<K, Array<String>> = mapOf(
        K.TAB_MAIN to arrayOf("Principale", "Main", "Principal", "Главная", "主页", "Principale"),
        K.TAB_EQ to arrayOf("Égaliseur", "Equalizer", "Ecualizador", "Эквалайзер", "均衡器", "Equalizzatore"),
        K.MENU_STOP to arrayOf("Arrêter le moteur", "Stop engine", "Detener el motor", "Остановить движок", "停止引擎", "Ferma il motore"),
        K.MENU_START to arrayOf("Démarrer le moteur", "Start engine", "Iniciar el motor", "Запустить движок", "启动引擎", "Avvia il motore"),
        K.MENU_BOOT to arrayOf("Démarrer avec le téléphone", "Start with phone", "Iniciar con el teléfono", "Запускать с телефоном", "开机自启动", "Avvia con il telefono"),
        K.MENU_RESTART to arrayOf("Relancer le moteur", "Restart engine", "Reiniciar el motor", "Перезапустить движок", "重启引擎", "Riavvia il motore"),
        K.MENU_LANGUAGE to arrayOf("Langue", "Language", "Idioma", "Язык", "语言", "Lingua"),
        K.VOLUME_BOOSTER to arrayOf("Volume\nBooster", "Volume\nBooster", "Booster de\nvolumen", "Усиление\nгромкости", "音量\n增强", "Booster\nvolume"),
        K.BASS_BOOSTER to arrayOf("Bass\nBooster", "Bass\nBooster", "Booster de\ngraves", "Усиление\nбасов", "低音\n增强", "Booster\nbassi"),
        K.NOW_PLAYING to arrayOf("En lecture", "Now playing", "En reproducción", "Сейчас играет", "正在播放", "In riproduzione"),
        K.PAUSED to arrayOf("En pause", "Paused", "En pausa", "Пауза", "已暂停", "In pausa"),
        K.ALLOW_TITLE to arrayOf(
            "Autoriser l'affichage du titre", "Allow track title display", "Permitir mostrar el título",
            "Разрешить показ названия", "允许显示曲目标题", "Consenti la visualizzazione del titolo"
        ),
        K.NO_MUSIC to arrayOf("Aucune musique détectée", "No music detected", "No se detecta música", "Музыка не обнаружена", "未检测到音乐", "Nessuna musica rilevata"),
        K.PRESETS to arrayOf("Préréglages", "Presets", "Preajustes", "Пресеты", "预设", "Preset"),
        K.CUSTOM to arrayOf("Personnalisé", "Custom", "Personalizado", "Свой", "自定义", "Personalizzato"),
        K.AUDIO_EFFECTS to arrayOf("Effets audio", "Audio effects", "Efectos de audio", "Аудиоэффекты", "音频效果", "Effetti audio"),
        K.WIDE to arrayOf("Stéréo large", "Wide stereo", "Estéreo amplio", "Широкое стерео", "宽立体声", "Stereo ampio"),
        K.CLARITY to arrayOf("Clarté", "Clarity", "Claridad", "Чёткость", "清晰度", "Chiarezza"),
        K.P_FLAT to arrayOf("Plat", "Flat", "Plano", "Ровный", "平直", "Piatto"),
        K.P_POP to arrayOf("Pop", "Pop", "Pop", "Поп", "流行", "Pop"),
        K.P_ROCK to arrayOf("Rock", "Rock", "Rock", "Рок", "摇滚", "Rock"),
        K.P_HIPHOP to arrayOf("Hip-Hop", "Hip-Hop", "Hip-Hop", "Хип-хоп", "嘻哈", "Hip-Hop"),
        K.P_ELECTRONIC to arrayOf("Électronique", "Electronic", "Electrónica", "Электроника", "电子", "Elettronica"),
        K.P_JAZZ to arrayOf("Jazz", "Jazz", "Jazz", "Джаз", "爵士", "Jazz"),
        K.P_CLASSICAL to arrayOf("Classique", "Classical", "Clásica", "Классика", "古典", "Classica"),
        K.P_VOCAL to arrayOf("Vocal", "Vocal", "Vocal", "Вокал", "人声", "Voce"),
        K.NOTIF_CHANNEL to arrayOf("Moteur audio", "Audio engine", "Motor de audio", "Аудиодвижок", "音频引擎", "Motore audio"),
        K.NOTIF_TEXT to arrayOf(
            "Moteur global actif sur tout le son du téléphone",
            "Global engine active on all phone audio",
            "Motor global activo en todo el audio del teléfono",
            "Глобальный движок работает для всего звука телефона",
            "全局引擎已作用于手机的所有声音",
            "Motore globale attivo su tutto l'audio del telefono"
        ),
        K.NOTIF_STOP to arrayOf("Désactiver", "Turn off", "Desactivar", "Выключить", "关闭", "Disattiva")
    )

    /** Identifiant interne des préréglages (inchangé, sauvegardé dans l'état) → clé de texte. */
    private val PRESET_KEYS = mapOf(
        "Plat" to K.P_FLAT, "Pop" to K.P_POP, "Rock" to K.P_ROCK, "Hip-Hop" to K.P_HIPHOP,
        "Électronique" to K.P_ELECTRONIC, "Jazz" to K.P_JAZZ, "Classique" to K.P_CLASSICAL, "Vocal" to K.P_VOCAL
    )

    /** Langue choisie ; vide = langue du téléphone si prise en charge, sinon anglais. */
    fun resolve(code: String): Lang {
        Lang.entries.firstOrNull { it.code == code }?.let { return it }
        val sys = Locale.getDefault().language
        return Lang.entries.firstOrNull { it.code == sys } ?: Lang.EN
    }

    fun get(k: K, lang: Lang = resolve(EqEngine.settings.value.lang)): String =
        T.getValue(k)[lang.ordinal]

    fun preset(id: String, lang: Lang): String = PRESET_KEYS[id]?.let { get(it, lang) } ?: id
}

/** Texte traduit, recomposé automatiquement quand la langue change. */
@Composable
fun tr(k: K): String {
    val st by EqEngine.settings.collectAsStateWithLifecycle()
    return I18n.get(k, I18n.resolve(st.lang))
}

@Composable
fun trPreset(id: String): String {
    val st by EqEngine.settings.collectAsStateWithLifecycle()
    return I18n.preset(id, I18n.resolve(st.lang))
}
