package com.icanteen.light.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.icanteen.light.data.CanteenRepository
import com.icanteen.light.data.OrderStatus
import com.icanteen.light.data.PreferencesManager
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONArray
import org.json.JSONObject

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
            val rootObj = JSONObject()
            
            // Info o uživateli
            if (menuData.userInfo != null) {
                val userObj = JSONObject()
                userObj.put("username", menuData.userInfo.username)
                userObj.put("credit", menuData.userInfo.credit)
                rootObj.put("userInfo", userObj)
            }

            val daysArray = JSONArray()
            var todayWidgetText = "Na nejbližší dny nemáš objednáno"

            // Uložíme dny do JSON pro UI
            for (day in menuData.days) {
                val dayObj = JSONObject()
                dayObj.put("dayName", day.dayName)
                dayObj.put("dateStr", day.dateStr)

                val mealsArray = JSONArray()
                for (meal in day.meals) {
                    val mealObj = JSONObject()
                    mealObj.put("mealNumber", meal.mealNumber)
                    mealObj.put("mealName", meal.mealName)
                    mealObj.put("status", meal.status.name)
                    mealObj.put("isOrdered", meal.isOrdered)
                    mealsArray.put(mealObj)
                }
                dayObj.put("meals", mealsArray)
                daysArray.put(dayObj)
            }
            rootObj.put("days", daysArray)

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

            prefs.saveCachedMenu(rootObj.toString())
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
