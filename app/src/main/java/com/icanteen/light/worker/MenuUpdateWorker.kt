package com.icanteen.light.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.icanteen.light.data.CanteenRepository
import com.icanteen.light.data.OrderStatus
import com.icanteen.light.data.PreferencesManager
import com.icanteen.light.data.toJson
import kotlinx.coroutines.flow.firstOrNull

class MenuUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = PreferencesManager(applicationContext)
        val user = prefs.usernameFlow.firstOrNull() ?: return Result.failure()
        val pass = prefs.passwordFlow.firstOrNull() ?: return Result.failure()
        val baseUrl = prefs.baseUrlFlow.firstOrNull() ?: "https://stravovani.sspbrno.cz"

        val repo = CanteenRepository(baseUrl)
        val loggedIn = repo.login(user, pass)
        if (!loggedIn) return Result.retry()

        val menuData = repo.fetchMenu()
        if (menuData != null) {
            val nowMs = System.currentTimeMillis()
            prefs.saveCachedMenu(menuData.toJson(), nowMs)

            // Najdeme první objednaný oběd pro Widget (POUZE PRO DNEŠEK)
            var mealNum = ""
            var mealName = "Dnes nemáš objednáno"
            
            val currentShortFormat = java.text.SimpleDateFormat("d. M.", java.util.Locale("cs", "CZ"))
            val currentDateStr = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale("cs", "CZ")).format(java.util.Date())
            val shortDate = currentShortFormat.format(java.util.Date())

            val todayMenu = menuData.days.firstOrNull { d -> 
                d.dateStr == currentDateStr || d.dateStr.contains(shortDate)
            }

            if (todayMenu != null) {
                val orderedLunch = todayMenu.orderedMeal
                if (orderedLunch != null) {
                    val statusPrefix = if (orderedLunch.status == OrderStatus.SERVED) "Vydáno: " else ""
                    mealNum = "Oběd č. ${orderedLunch.mealNumber}"
                    mealName = "$statusPrefix${orderedLunch.mealName}"
                } else {
                    mealNum = "-"
                    mealName = "Na dnešek nemáš oběd"
                }
            } else {
                 mealNum = "-"
                 mealName = "Dnes není obědový den"
            }

            val dateFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val syncTime = dateFormat.format(java.util.Date())

            prefs.saveWidgetData(mealNum, mealName, syncTime)

            // ── New meals notification ──────────────────────────────
            // Logic: compare total number of days that have at least one meal.
            // iCanteen typically loads meals 3-4 weeks ahead. A new batch upload
            // pushes the count above the previously stored value.
            // Days with 0 meals are excluded (holidays / weekends).
            val notifNewMealsEnabled = prefs.notifNewMealsEnabledFlow.firstOrNull() ?: false
            if (notifNewMealsEnabled) {
                // Find the furthest date with any meals (non-empty days only)
                val dateParser = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale("cs", "CZ"))
                val furthestDate = menuData.days
                    .filter { it.meals.isNotEmpty() }
                    .mapNotNull { day ->
                        runCatching { dateParser.parse(day.dateStr) }.getOrNull()
                    }
                    .maxOrNull()

                if (furthestDate != null) {
                    val furthestDateStr = dateParser.format(furthestDate)
                    val previousDateStr = prefs.updateKnownMenuLastDate(furthestDateStr)
                    // Fire only when the horizon genuinely moved forward (new month uploaded)
                    // and we have a previously stored reference (not first run)
                    val previousDate = if (previousDateStr.isNotEmpty())
                        runCatching { dateParser.parse(previousDateStr) }.getOrNull()
                    else null

                    if (previousDate != null && furthestDate.after(previousDate)) {
                        NotificationHelper.showNotification(
                            applicationContext,
                            202,
                            "Nové obědy v systému",
                            "Do systému byly nahrány nové obědy!"
                        )
                    }
                }
            }

            // Aktualizace widgetu
            try {
                com.icanteen.light.widget.updateAllWidgets(applicationContext)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            return Result.success()
        }

        return Result.retry()
    }
}
