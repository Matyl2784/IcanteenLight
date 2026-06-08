package com.icanteen.light.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "canteen_prefs")

class PreferencesManager(private val context: Context) {
    companion object {
        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_PASSWORD = stringPreferencesKey("password")
        val KEY_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val KEY_CACHED_MENU = stringPreferencesKey("cached_menu_json")
        val KEY_TODAY_WIDGET_TEXT = stringPreferencesKey("today_widget_text") // Deprecated
        val KEY_WIDGET_MEAL_NUMBER = stringPreferencesKey("widget_meal_number")
        val KEY_WIDGET_MEAL_NAME = stringPreferencesKey("widget_meal_name")
        val KEY_WIDGET_LAST_SYNC = stringPreferencesKey("widget_last_sync")
        val KEY_BASE_URL = stringPreferencesKey("base_url")
        val KEY_THEME_COLOR = stringPreferencesKey("theme_color")
        val KEY_DARK_MODE = stringPreferencesKey("dark_mode")

        val KEY_NOTIF_WARNING_ENABLED = booleanPreferencesKey("notif_warning_enabled")
        val KEY_NOTIF_WARNING_TIME = stringPreferencesKey("notif_warning_time")
        val KEY_NOTIF_WARNING_DAYS = intPreferencesKey("notif_warning_days")
        
        val KEY_NOTIF_DAILY_ENABLED = booleanPreferencesKey("notif_daily_enabled")
        val KEY_NOTIF_DAILY_TIME = stringPreferencesKey("notif_daily_time")
        
        // New meals notification
        val KEY_NOTIF_NEW_MEALS_ENABLED = booleanPreferencesKey("notif_new_meals_enabled")
        val KEY_KNOWN_MENU_LAST_DATE = stringPreferencesKey("known_menu_last_date")
    }

    val usernameFlow: Flow<String?> = context.dataStore.data.map { it[KEY_USERNAME] }
    val passwordFlow: Flow<String?> = context.dataStore.data.map { it[KEY_PASSWORD] }
    val isLoggedInFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_LOGGED_IN] ?: false }
    val cachedMenuFlow: Flow<String?> = context.dataStore.data.map { it[KEY_CACHED_MENU] }
    val baseUrlFlow: Flow<String> = context.dataStore.data.map { it[KEY_BASE_URL] ?: "https://stravovani.sspbrno.cz" }
    val themeColorFlow: Flow<String> = context.dataStore.data.map { it[KEY_THEME_COLOR] ?: "Neon Teal" }
    val darkModeFlow: Flow<String> = context.dataStore.data.map { it[KEY_DARK_MODE] ?: "Systémový" }
    
    val notifWarningEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIF_WARNING_ENABLED] ?: false }
    val notifWarningTimeFlow: Flow<String> = context.dataStore.data.map { it[KEY_NOTIF_WARNING_TIME] ?: "18:00" }
    val notifWarningDaysFlow: Flow<Int> = context.dataStore.data.map { it[KEY_NOTIF_WARNING_DAYS] ?: 2 }
    
    val notifDailyEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIF_DAILY_ENABLED] ?: false }
    val notifDailyTimeFlow: Flow<String> = context.dataStore.data.map { it[KEY_NOTIF_DAILY_TIME] ?: "11:30" }
    val notifNewMealsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIF_NEW_MEALS_ENABLED] ?: false }
    val knownMenuLastDateFlow: Flow<String> = context.dataStore.data.map { it[KEY_KNOWN_MENU_LAST_DATE] ?: "" }

    suspend fun saveCredentials(user: String, pass: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USERNAME] = user
            prefs[KEY_PASSWORD] = pass
            prefs[KEY_LOGGED_IN] = true
        }
    }

    suspend fun clearCredentials() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_USERNAME)
            prefs.remove(KEY_PASSWORD)
            prefs[KEY_LOGGED_IN] = false
            prefs.remove(KEY_CACHED_MENU)
        }
    }

    suspend fun saveCachedMenu(json: String) {
        context.dataStore.edit { it[KEY_CACHED_MENU] = json }
    }

    suspend fun saveWidgetText(text: String) {
        context.dataStore.edit { it[KEY_TODAY_WIDGET_TEXT] = text }
    }
    
    suspend fun saveBaseUrl(url: String) {
        context.dataStore.edit { it[KEY_BASE_URL] = url }
    }
    
    suspend fun saveThemeColor(color: String) {
        context.dataStore.edit { it[KEY_THEME_COLOR] = color }
    }
    
    suspend fun saveDarkMode(mode: String) {
        context.dataStore.edit { it[KEY_DARK_MODE] = mode }
    }
    
    suspend fun saveWidgetData(mealNumber: String, mealName: String, syncTime: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_WIDGET_MEAL_NUMBER] = mealNumber
            prefs[KEY_WIDGET_MEAL_NAME] = mealName
            prefs[KEY_WIDGET_LAST_SYNC] = syncTime
        }
    }
    
    suspend fun saveNotifWarning(enabled: Boolean, time: String, daysAhead: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTIF_WARNING_ENABLED] = enabled
            prefs[KEY_NOTIF_WARNING_TIME] = time
            prefs[KEY_NOTIF_WARNING_DAYS] = daysAhead
        }
    }
    
    suspend fun saveNotifDaily(enabled: Boolean, time: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTIF_DAILY_ENABLED] = enabled
            prefs[KEY_NOTIF_DAILY_TIME] = time
        }
    }
    
    suspend fun saveNotifNewMeals(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIF_NEW_MEALS_ENABLED] = enabled }
    }

    /**
     * Updates the stored "furthest known meal date" and returns the previous value.
     * Used to detect when a new batch of meals is uploaded to iCanteen.
     */
    suspend fun updateKnownMenuLastDate(newDate: String): String {
        val old = knownMenuLastDateFlow.first()
        context.dataStore.edit { it[KEY_KNOWN_MENU_LAST_DATE] = newDate }
        return old
    }
}
