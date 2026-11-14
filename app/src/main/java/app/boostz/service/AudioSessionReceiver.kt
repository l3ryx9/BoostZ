package app.boostz.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import app.boostz.audio.EqEngine

/**
 * Reçoit l'annonce des sessions audio des lecteurs de musique
 * (ACTION_OPEN / CLOSE_AUDIO_EFFECT_CONTROL_SESSION) et les transmet au moteur.
 * Déclaré dans le manifeste, et aussi enregistré à l'exécution par [EqService] :
 * traiter deux fois la même annonce est sans effet.
 */
class AudioSessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        EqEngine.init(context.applicationContext)
        val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
        when (intent.action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> EqEngine.openSession(session)
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> EqEngine.closeSession(session)
        }
    }

    companion object {
        fun filter() = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
    }
}
