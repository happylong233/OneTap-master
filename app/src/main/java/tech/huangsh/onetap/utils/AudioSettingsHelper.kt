package tech.huangsh.onetap.utils

import android.content.Context
import android.media.AudioManager
import android.util.Log

object AudioSettingsHelper {
    private const val TAG = "AudioSettingsHelper"

    fun maximizeCommonStreams(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        listOf(
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_SYSTEM
        ).forEach { stream ->
            runCatching {
                audioManager.setStreamVolume(stream, audioManager.getStreamMaxVolume(stream), 0)
            }.onFailure {
                Log.w(TAG, "Unable to maximize stream $stream", it)
            }
        }
    }
}
