package app.boostz.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.boostz.audio.EqEngine

/** Relance le moteur après un redémarrage si l'utilisateur l'avait laissé allumé. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != "android.intent.action.QUICKBOOT_POWERON") return

        val app = context.applicationContext
        EqEngine.init(app)
        if (!EqEngine.state.value.enabled) return

        if (EqEngine.settings.value.startOnBoot) {
            EqEngine.setPower(app, true)
        } else {
            EqEngine.setEnabled(false)
        }
    }
}
