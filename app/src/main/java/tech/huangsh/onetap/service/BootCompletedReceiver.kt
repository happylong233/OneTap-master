package tech.huangsh.onetap.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tech.huangsh.onetap.data.repository.SettingsRepository
import tech.huangsh.onetap.utils.TimeAnnouncementScheduler

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsRepository(context.applicationContext).settings.first()
                TimeAnnouncementScheduler.reschedule(
                    context.applicationContext,
                    settings.hourlyTimeAnnouncementEnabled,
                    settings.hourlyTimeAnnouncementHours
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
