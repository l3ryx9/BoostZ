package app.boostz

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.boostz.audio.EqEngine
import app.boostz.audio.NowPlaying
import app.boostz.ui.BoostRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EqEngine.init(applicationContext)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        if (EqEngine.state.value.enabled) {
            EqEngine.setPower(applicationContext, true)
        }
        setContent { BoostRoot() }
    }

    override fun onResume() {
        super.onResume()
        NowPlaying.start(this)
    }
}
