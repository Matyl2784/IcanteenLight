package com.icanteen.light.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.icanteen.light.data.MenuData
import com.icanteen.light.data.OrderStatus
import com.icanteen.light.data.PreferencesManager
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                AlarmScheduler.scheduleAlarms(context)
            }
            AlarmScheduler.ACTION_DAILY_INFO -> {
                runBlocking { processDailyInfo(context) }
                AlarmScheduler.scheduleAlarms(context) // Reschedule for next day
            }
            AlarmScheduler.ACTION_WARNING -> {
                runBlocking { processWarning(context) }
                AlarmScheduler.scheduleAlarms(context) // Reschedule for next day
            }
            AlarmScheduler.ACTION_UPDATE_WIDGET -> {
                val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.icanteen.light.worker.MenuUpdateWorker>().build()
                androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                    "CanteenMenuUpdateOneTime",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    workRequest
                )
                AlarmScheduler.scheduleAlarms(context) // Reschedule for next day
            }
        }
    }

    private suspend fun processDailyInfo(context: Context) {
        val prefs = PreferencesManager(context)
        val jsonString = prefs.cachedMenuFlow.firstOrNull() ?: return
        try {
            val root = JSONObject(jsonString)
            // CanteenRepository structure... 
            // We just need the first day which is typically Today or closest next day.
            // But we specifically only want to notify if the FIRST day in the list is strictly TODAY.
            // A simple logic: if it's the exact current date, we show it. Or to be safe, just show first ordered/available.
            val daysObj = root.optJSONArray("days") ?: return
            if (daysObj.length() > 0) {
                val todayObj = daysObj.getJSONObject(0)
                val isToday = todayObj.optBoolean("isToday", false)
                
                // If the app doesn't save explicitly "isToday", we can just analyze 'dateStr' 
                // Or simply notify the next active day. The user asked "kazdy den ktery je v appce den".
                // We'll notify if the first index is ordered or not for "Dnešní oběd".
                
                val dateStr = todayObj.optString("dateStr")
                val dayName = todayObj.optString("dayName")
                
                // Canteen format: 08.04.2026
                val currentFormat = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale("cs", "CZ"))
                val currentDateStr = currentFormat.format(java.util.Date())
                
                if (dateStr == currentDateStr) {
                    // It is today! Check ordered meal
                    val mealsObj = todayObj.optJSONArray("meals")
                    var orderedStr: String? = null
                    
                    if (mealsObj != null) {
                        for (i in 0 until mealsObj.length()) {
                            val m = mealsObj.getJSONObject(i)
                            val status = m.optString("status")
                            if (status == OrderStatus.ORDERED.name || status == OrderStatus.ORDERED_LOCKED.name || status == OrderStatus.SERVED.name) {
                                orderedStr = "🥗 Oběd č. ${m.optString("mealNumber")}: ${m.optString("mealName")}"
                                break
                            }
                        }
                    }
                    
                    if (orderedStr != null) {
                        NotificationHelper.showNotification(
                            context, 201, "Dnešní oběd", orderedStr
                        )
                    } else {
                        NotificationHelper.showNotification(
                            context, 201, "Dnes jsi bez oběda", "Na dnešek nemáš nic objednáno."
                        )
                    }
                } else {
                    // Canteen format could be different, fallback: Just check if the parsed list's first item is physically today's date
                    val currentShortFormat = java.text.SimpleDateFormat("d. M.", java.util.Locale("cs", "CZ"))
                    val shortDate = currentShortFormat.format(java.util.Date())
                    if (dateStr.contains(shortDate)) {
                        val mealsObj = todayObj.optJSONArray("meals")
                        var orderedStr: String? = null
                        if (mealsObj != null) {
                            for (i in 0 until mealsObj.length()) {
                                val m = mealsObj.getJSONObject(i)
                                val status = m.optString("status")
                                if (status == OrderStatus.ORDERED.name || status == OrderStatus.ORDERED_LOCKED.name || status == OrderStatus.SERVED.name) {
                                    orderedStr = "🥗 Oběd č. ${m.optString("mealNumber")}: ${m.optString("mealName")}"
                                    break
                                }
                            }
                        }
                        if (orderedStr != null) {
                            NotificationHelper.showNotification(context, 201, "Dnešní oběd", orderedStr)
                        } else {
                            NotificationHelper.showNotification(context, 201, "Dnes jsi bez oběda", "Nemáš nic objednáno.")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun processWarning(context: Context) {
        val prefs = PreferencesManager(context)
        val jsonString = prefs.cachedMenuFlow.firstOrNull() ?: return
        val daysAhead = prefs.notifWarningDaysFlow.firstOrNull() ?: 2
        
        try {
            val root = JSONObject(jsonString)
            val daysObj = root.optJSONArray("days") ?: return
            
            // We find the day at index x. If it exists and has NO ordered meal, we ping!
            if (daysAhead < daysObj.length()) {
                val targetDay = daysObj.getJSONObject(daysAhead)
                
                val mealsObj = targetDay.optJSONArray("meals")
                var hasOrder = false
                var isAvailable = false
                
                if (mealsObj != null) {
                    for (i in 0 until mealsObj.length()) {
                        val m = mealsObj.getJSONObject(i)
                        val status = m.optString("status")
                        if (status == OrderStatus.ORDERED.name || status == OrderStatus.ORDERED_LOCKED.name || status == OrderStatus.SERVED.name) {
                            hasOrder = true
                        }
                        if (status == OrderStatus.AVAILABLE.name || status == OrderStatus.ORDERED.name) {
                            isAvailable = true
                        }
                    }
                }
                
                if (!hasOrder && isAvailable) {
                    val dateStr = targetDay.optString("dateStr")
                    val dayName = targetDay.optString("dayName")
                    NotificationHelper.showNotification(
                        context, 202, "Nezapomeň si objednat!", "Za $daysAhead dny ($dayName $dateStr) aktuálně nemáš oběd!"
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
