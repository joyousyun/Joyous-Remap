package com.joyous.remap

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService

/**
 * Hace falta un servicio de escucha de notificaciones (vacío, no lee nada) porque Android solo deja
 * listar las sesiones multimedia a una app con ese acceso concedido por el usuario.
 */
class MediaListener : NotificationListenerService()

enum class MediaAct(val id: String, val label: Int) {
    NONE("none", R.string.ma_none),
    PLAY_PAUSE("play_pause", R.string.ma_play_pause),
    PLAY("play", R.string.ma_play),
    PAUSE("pause", R.string.ma_pause),
    NEXT("next", R.string.ma_next),
    PREV("prev", R.string.ma_prev),
    STOP("stop", R.string.ma_stop),
    FORWARD("forward", R.string.ma_forward),
    REWIND("rewind", R.string.ma_rewind),
    VOL_UP("vol_up", R.string.ma_vol_up),
    VOL_DOWN("vol_down", R.string.ma_vol_down);

    companion object {
        fun from(id: String?): MediaAct = values().firstOrNull { it.id == id } ?: NONE
    }
}

/** Control de la sesión multimedia correcta mediante las APIs oficiales (MediaSession). */
object MediaRemote {

    /** Una sesión en pausa solo cuenta si se tocó hace poco; si no, es una sesión olvidada. */
    private const val PAUSED_MAX_MS = 30 * 60 * 1000L

    fun listenerEnabled(c: Context): Boolean {
        val s = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners") ?: return false
        return s.split(':').any { ComponentName.unflattenFromString(it)?.packageName == c.packageName }
    }

    private fun playing(st: PlaybackState) =
        st.state == PlaybackState.STATE_PLAYING || st.state == PlaybackState.STATE_BUFFERING

    /**
     * Sesión adecuada: la que está sonando (la más reciente) o, si no hay ninguna, la última en
     * pausa reciente. Nunca la propia ni sesiones detenidas, con error o sin estado.
     */
    fun target(c: Context): MediaController? {
        if (!listenerEnabled(c)) return null
        val list = try {
            c.getSystemService(MediaSessionManager::class.java)
                .getActiveSessions(ComponentName(c, MediaListener::class.java))
        } catch (e: Exception) {
            return null
        }
        val now = SystemClock.elapsedRealtime()
        val ok = list.filter { s ->
            val st = s.playbackState ?: return@filter false
            s.packageName != c.packageName && (playing(st) ||
                (st.state == PlaybackState.STATE_PAUSED && now - st.lastPositionUpdateTime < PAUSED_MAX_MS))
        }
        return ok.firstOrNull { playing(it.playbackState!!) } ?: ok.firstOrNull()
    }

    /** Texto "app · estado" de la sesión que se controlaría ahora, o null. */
    fun describe(c: Context): Pair<String, Boolean>? {
        val t = target(c) ?: return null
        val name = try {
            c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(t.packageName, 0)).toString()
        } catch (e: Exception) {
            t.packageName
        }
        return name to playing(t.playbackState!!)
    }

    private fun has(acts: Long, vararg bits: Long) = bits.any { acts and it != 0L }

    /** Envía la orden solo si la sesión dice que la admite; devuelve si se envió. */
    fun send(c: Context, a: MediaAct): Boolean {
        val ctl = target(c) ?: return false
        val st = ctl.playbackState ?: return false
        val acts = st.actions
        val tc = ctl.transportControls
        val on = playing(st)
        return when (a) {
            MediaAct.NONE -> false
            MediaAct.PLAY_PAUSE ->
                if (on) {
                    if (has(acts, PlaybackState.ACTION_PAUSE, PlaybackState.ACTION_PLAY_PAUSE)) { tc.pause(); true } else false
                } else {
                    if (has(acts, PlaybackState.ACTION_PLAY, PlaybackState.ACTION_PLAY_PAUSE)) { tc.play(); true } else false
                }
            MediaAct.PLAY ->
                if (on) true
                else if (has(acts, PlaybackState.ACTION_PLAY, PlaybackState.ACTION_PLAY_PAUSE)) { tc.play(); true } else false
            MediaAct.PAUSE ->
                if (!on) true
                else if (has(acts, PlaybackState.ACTION_PAUSE, PlaybackState.ACTION_PLAY_PAUSE)) { tc.pause(); true } else false
            MediaAct.NEXT -> if (has(acts, PlaybackState.ACTION_SKIP_TO_NEXT)) { tc.skipToNext(); true } else false
            MediaAct.PREV -> if (has(acts, PlaybackState.ACTION_SKIP_TO_PREVIOUS)) { tc.skipToPrevious(); true } else false
            MediaAct.STOP -> if (has(acts, PlaybackState.ACTION_STOP)) { tc.stop(); true } else false
            MediaAct.FORWARD -> if (has(acts, PlaybackState.ACTION_FAST_FORWARD)) { tc.fastForward(); true } else false
            MediaAct.REWIND -> if (has(acts, PlaybackState.ACTION_REWIND)) { tc.rewind(); true } else false
            MediaAct.VOL_UP -> { ctl.adjustVolume(AudioManager.ADJUST_RAISE, 0); true }
            MediaAct.VOL_DOWN -> { ctl.adjustVolume(AudioManager.ADJUST_LOWER, 0); true }
        }
    }
}
