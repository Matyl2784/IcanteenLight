package com.icanteen.light.data

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
