package app.boostz.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import app.boostz.audio.EqEngine

/**
 * Renvoie une fonction pour allumer / éteindre le moteur.
 * Au premier allumage, demande l'autorisation d'afficher la notification du service.
 */
@Composable
fun rememberPower(): (Boolean) -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return { on ->
        if (on && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        EqEngine.setPower(context, on)
    }
}

/** Les réglages du son allument le moteur automatiquement s'il était arrêté. */
@Composable
fun rememberEnsureOn(): () -> Unit {
    val power = rememberPower()
    return {
        if (!EqEngine.state.value.enabled) power(true)
    }
}
