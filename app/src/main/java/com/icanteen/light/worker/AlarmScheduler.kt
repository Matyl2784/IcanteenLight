package com.icanteen.light.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.icanteen.light.data.PreferencesManager
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import java.util.Calendar

object AlarmScheduler {

    const val ACTION_DAILY_INFO = "com.icanteen.light.ACTION_DAILY_INFO"
    const val ACTION_WARNING = "com.icanteen.light.ACTION_WARNING"
    const val ACTION_UPDATE_WIDGET = "com.icanteen.light.ACTION_UPDATE_WIDGET"

    fun scheduleAlarms(context: Context) {
        runBlocking {
            val prefs = PreferencesManager(context)
            
            val dailyEnabled = prefs.notifDailyEnabledFlow.firstOrNull() ?: false
            val dailyTime = prefs.notifDailyTimeFlow.firstOrNull() ?: "11:30"
            
            val warningEnabled = prefs.notifWarningEnabledFlow.firstOrNull() ?: false
            val warningTime = prefs.notifWarningTimeFlow.firstOrNull() ?: "18:00"
            
            val widgetUpdateTime = prefs.widgetUpdateTimeFlow.firstOrNull() ?: "03:00"

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // Daily Info
            val dailyIntent = Intent(context, AlarmReceiver::class.java).apply { action = ACTION_DAILY_INFO }
            val dailyPendingIntent = PendingIntent.getBroadcast(
                context, 101, dailyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (dailyEnabled) {
                val cal = getNextTimeMillis(dailyTime)
                if (canScheduleExactAlarms(alarmManager)) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal, dailyPendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal, dailyPendingIntent)
                }
            } else {
                alarmManager.cancel(dailyPendingIntent)
            }

            // Warning
            val warningIntent = Intent(context, AlarmReceiver::class.java).apply { action = ACTION_WARNING }
            val warningPendingIntent = PendingIntent.getBroadcast(
                context, 102, warningIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (warningEnabled) {
                val cal = getNextTimeMillis(warningTime)
                if (canScheduleExactAlarms(alarmManager)) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal, warningPendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal, warningPendingIntent)
                }
            } else {
                alarmManager.cancel(warningPendingIntent)
            }
            
            // Widget Update
            val widgetIntent = Intent(context, AlarmReceiver::class.java).apply { action = ACTION_UPDATE_WIDGET }
            val widgetPendingIntent = PendingIntent.getBroadcast(
                context, 103, widgetIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val widgetCal = getNextTimeMillis(widgetUpdateTime)
            if (canScheduleExactAlarms(alarmManager)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, widgetCal, widgetPendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, widgetCal, widgetPendingIntent)
            }
        }
    }

    private fun getNextTimeMillis(timeStr: String): Long {
        val parts = timeStr.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 12
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1) // Už to dneska bylo, zítra
        }
        return calendar.timeInMillis
    }

    private fun canScheduleExactAlarms(alarmManager: AlarmManager): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }
}
