package app.boostz.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import app.boostz.audio.EqEngine
import app.boostz.audio.SmartEq

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

/** Accès à l'analyse du son global (Auto EQ). */
class AnalysisAccess(val granted: Boolean, val request: () -> Unit)

/**
 * L'Auto EQ lit le son global du téléphone (session audio 0). Android classe cette lecture
 * dans l'autorisation d'enregistrement audio : on l'explique clairement avant de la demander.
 */
@Composable
fun rememberAnalysisAccess(): AnalysisAccess {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(SmartEq.hasPermission(context)) }
    var ask by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) SmartEq.retry()
    }
    if (ask) {
        AlertDialog(
            onDismissRequest = { ask = false },
            containerColor = CardHi,
            title = { Text("Analyse du son global", color = TextHi) },
            text = {
                Text(
                    "L'Auto EQ écoute le son qui sort du téléphone, toutes applications confondues, " +
                        "pour régler l'égaliseur selon la musique jouée.\n\n" +
                        "Android range cette lecture dans l'autorisation « Micro » (enregistrement audio). " +
                        "BoostZ n'allume jamais le micro et n'enregistre rien.\n\n" +
                        "Sans cette autorisation, l'Auto EQ applique seulement des corrections de base.",
                    color = TextLo
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    ask = false
                    launcher.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text("Continuer", color = Lime) }
            },
            dismissButton = {
                TextButton(onClick = { ask = false }) { Text("Plus tard", color = TextLo) }
            }
        )
    }
    return AnalysisAccess(granted) { if (!granted) ask = true }
}
