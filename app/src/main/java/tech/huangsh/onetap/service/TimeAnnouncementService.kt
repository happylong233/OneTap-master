package tech.huangsh.onetap.service

import android.app.Service
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import tech.huangsh.onetap.R
import tech.huangsh.onetap.utils.AudioSettingsHelper
import java.util.Locale

class TimeAnnouncementService : Service(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var pendingHour: Int = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        pendingHour = intent?.getIntExtra(EXTRA_HOUR, -1)?.takeIf { it in 0..23 }
            ?: java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        startForeground(NOTIFICATION_ID, buildNotification())
        AudioSettingsHelper.maximizeCommonStreams(this)
        tts?.shutdown()
        tts = TextToSpeech(this, this)
        handler.postDelayed({ stopSelf(startId) }, 15000L)
        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            stopSelf()
            return
        }

        tts?.language = Locale.CHINA
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                stopSelf()
            }

            @Deprecated("Deprecated by Android")
            override fun onError(utteranceId: String?) {
                stopSelf()
            }
        })

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        tts?.speak(buildAnnouncement(pendingHour), TextToSpeech.QUEUE_FLUSH, params, "time_announcement")
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildAnnouncement(hour: Int): String {
        return when (hour) {
            0 -> "现在是凌晨12点整"
            in 1..5 -> "现在是凌晨${hour}点整"
            in 6..11 -> "现在是上午${hour}点整"
            12 -> "现在是中午12点整"
            in 13..17 -> "现在是下午${hour - 12}点整"
            else -> "现在是晚上${hour - 12}点整"
        }
    }

    companion object {
        const val EXTRA_HOUR = "extra_hour"
        private const val CHANNEL_ID = "time_announcement"
        private const val NOTIFICATION_ID = 2302
    }

    private fun buildNotification(): android.app.Notification {
        ensureNotificationChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("整点报时")
            .setContentText("正在播报当前时间")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "整点报时",
            NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(channel)
    }
}
