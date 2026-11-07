package app.boostz.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.core.app.NotificationManagerCompat
import app.boostz.service.MediaListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Track(val title: String, val artist: String?, val playing: Boolean)

/** Titre en cours de lecture dans n'importe quel lecteur (Spotify, YouTube Music, VLC…). */
object NowPlaying {
    private val _track = MutableStateFlow<Track?>(null)
    val track: StateFlow<Track?> = _track.asStateFlow()

    private val _access = MutableStateFlow(false)
    val access: StateFlow<Boolean> = _access.asStateFlow()

    private var manager: MediaSessionManager? = null
    private var controllers: List<MediaController> = emptyList()
    private var current: MediaController? = null

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onSessionDestroyed() = refresh()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        attach(list ?: emptyList())
    }

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** À appeler depuis le fil principal (onResume de l'activité). */
    fun start(context: Context) {
        val app = context.applicationContext
        val ok = hasAccess(app)
        _access.value = ok
        if (!ok) return
        val cn = ComponentName(app, MediaListener::class.java)
        val m = manager ?: app.getSystemService(MediaSessionManager::class.java) ?: return
        runCatching {
            if (manager == null) {
                m.addOnActiveSessionsChangedListener(sessionsListener, cn)
                manager = m
            }
            attach(m.getActiveSessions(cn))
        }
    }

    /** Ouvre l'application qui joue la musique. */
    fun openPlayer(context: Context) {
        val c = current ?: return
        val pending = c.sessionActivity
        if (pending != null) {
            runCatching { pending.send() }
            return
        }
        context.packageManager.getLaunchIntentForPackage(c.packageName)?.let {
            runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    /** Ouvre l'écran système pour autoriser la lecture du titre en cours. */
    fun openAccessSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun attach(list: List<MediaController>) {
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = list
        list.forEach { runCatching { it.registerCallback(callback) } }
        refresh()
    }

    private fun refresh() {
        val c = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull()
        current = c
        if (c == null) {
            _track.value = null
            return
        }
        val md = c.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        _track.value = if (title.isNullOrBlank()) null else Track(
            title = title,
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        )
    }
}
