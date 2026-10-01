package com.icanteen.light.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Represent the status of a specific meal option.
 */
enum class OrderStatus {
    ORDERED,        // User has ordered this meal, can be cancelled
    ORDERED_LOCKED, // User has ordered this, but cannot change it anymore
    AVAILABLE,      // User can order this meal
    LOCKED,         // Order deadline has passed, not ordered
    SERVED,         // Meal has already been served/collected
    NOT_AVAILABLE   // Meal is listed but cannot be ordered
}

/**
 * Represents a single meal option (e.g., Lunch 1, Lunch 2).
 */
data class LunchItem(
    val mealNumber: String, // e.g., "1", "2", "3", "D"
    val mealName: String,
    val status: OrderStatus,
    val orderCommand: String? = null // The JS command or ID to order/cancel
) {
    val isOrdered: Boolean 
        get() = (status == OrderStatus.ORDERED || status == OrderStatus.ORDERED_LOCKED || status == OrderStatus.SERVED)
}

/**
 * Represents a full menu for a single day.
 */
data class DayMenu(
    val dayName: String, // e.g., "Pondělí"
    val dateStr: String, // e.g., "20.04.2026"
    val meals: List<LunchItem>,
    val isHoliday: Boolean = false
) {
    // Helper to find if anything is ordered this day
    val orderedMeal: LunchItem? get() = meals.find { it.isOrdered }
}

data class UserInfo(
    val username: String,
    val credit: String
)

data class MenuData(
    val userInfo: UserInfo?,
    val days: List<DayMenu>
)

fun MenuData.toJson(): String {
    val rootObj = JSONObject()
    if (userInfo != null) {
        val userObj = JSONObject()
        userObj.put("username", userInfo.username)
        userObj.put("credit", userInfo.credit)
        rootObj.put("userInfo", userObj)
    }

    val daysArray = JSONArray()
    for (day in days) {
        val dayObj = JSONObject()
        dayObj.put("dayName", day.dayName)
        dayObj.put("dateStr", day.dateStr)
        dayObj.put("isHoliday", day.isHoliday)

        val mealsArray = JSONArray()
        for (meal in day.meals) {
            val mealObj = JSONObject()
            mealObj.put("mealNumber", meal.mealNumber)
            mealObj.put("mealName", meal.mealName)
            mealObj.put("status", meal.status.name)
            mealObj.put("isOrdered", meal.isOrdered)
            if (meal.orderCommand != null) {
                mealObj.put("orderCommand", meal.orderCommand)
            }
            mealsArray.put(mealObj)
        }
        dayObj.put("meals", mealsArray)
        daysArray.put(dayObj)
    }
    rootObj.put("days", daysArray)
    return rootObj.toString()
}

fun parseMenuJson(jsonString: String?): MenuData? {
    if (jsonString.isNullOrEmpty()) return null
    return try {
        val rootObj = JSONObject(jsonString)
        val userObj = rootObj.optJSONObject("userInfo")
        val userInfo = if (userObj != null) {
            UserInfo(
                username = userObj.optString("username", ""),
                credit = userObj.optString("credit", "")
            )
        } else null

        val daysArray = rootObj.optJSONArray("days") ?: JSONArray()
        val daysList = mutableListOf<DayMenu>()
        for (i in 0 until daysArray.length()) {
            val dayObj = daysArray.getJSONObject(i)
            val dayName = dayObj.optString("dayName", "")
            val dateStr = dayObj.optString("dateStr", "")
            val isHoliday = dayObj.optBoolean("isHoliday", false)

            val mealsArray = dayObj.optJSONArray("meals") ?: JSONArray()
            val mealsList = mutableListOf<LunchItem>()
            for (j in 0 until mealsArray.length()) {
                val mealObj = mealsArray.getJSONObject(j)
                val mealNumber = mealObj.optString("mealNumber", "")
                val mealName = mealObj.optString("mealName", "")
                val statusStr = mealObj.optString("status", OrderStatus.NOT_AVAILABLE.name)
                val status = runCatching { OrderStatus.valueOf(statusStr) }.getOrDefault(OrderStatus.NOT_AVAILABLE)
                val orderCommand = if (mealObj.has("orderCommand") && !mealObj.isNull("orderCommand")) mealObj.optString("orderCommand") else null

                mealsList.add(LunchItem(mealNumber, mealName, status, orderCommand))
            }
            daysList.add(DayMenu(dayName, dateStr, mealsList, isHoliday))
        }
        MenuData(userInfo, daysList)
    } catch (e: Exception) {
        null
    }
}

fun formatLastUpdated(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""

    val date = Date(timestampMs)
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { time = date }

    val timeFormat = SimpleDateFormat("HH:mm", Locale("cs", "CZ"))
    val timeStr = timeFormat.format(date)

    val isSameYear = now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
    val isSameDay = isSameYear && now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)

    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = isSameYear && yesterday.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)

    return when {
        isSameDay -> "dnes v $timeStr"
        isYesterday -> "včera v $timeStr"
        else -> {
            val dateFormat = SimpleDateFormat("d. M. v HH:mm", Locale("cs", "CZ"))
            dateFormat.format(date)
        }
    }
}

