package app.boostz

import android.app.Application
import app.boostz.audio.EqEngine

class BoostApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Le moteur est initialisé dès que le processus démarre (activité, service ou démarrage du téléphone).
        EqEngine.init(this)
    }
}
