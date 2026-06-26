package tech.huangsh.onetap.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tech.huangsh.onetap.data.repository.SettingsRepository
import tech.huangsh.onetap.utils.TimeAnnouncementScheduler
import java.util.Calendar

class TimeAnnouncementReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = SettingsRepository(context.applicationContext)
                val settings = repository.settings.first()
                val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

                if (settings.hourlyTimeAnnouncementEnabled && currentHour in settings.hourlyTimeAnnouncementHours) {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, TimeAnnouncementService::class.java)
                            .putExtra(TimeAnnouncementService.EXTRA_HOUR, currentHour)
                    )
                }

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
