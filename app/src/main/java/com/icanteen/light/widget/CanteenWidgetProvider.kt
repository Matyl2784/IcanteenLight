package com.icanteen.light.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.icanteen.light.MainActivity
import com.icanteen.light.R
import com.icanteen.light.data.PreferencesManager
import com.icanteen.light.data.dataStore
import com.icanteen.light.worker.MenuUpdateWorker
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

abstract class BaseWidgetProvider : AppWidgetProvider() {
    abstract val layoutId: Int

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, layoutId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_SYNC) {
            // Zobrazení textu "Aktualizuji..." hned po kliknutí? Můžeme hromadně updatnout všechny.
            val workRequest = OneTimeWorkRequestBuilder<MenuUpdateWorker>().build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}

class CanteenWidgetProvider : BaseWidgetProvider() {
    override val layoutId = R.layout.widget_layout
}

class CanteenWidgetProviderHalf : BaseWidgetProvider() {
    override val layoutId = R.layout.widget_layout_half
}

class CanteenWidgetProviderTiny : BaseWidgetProvider() {
    override val layoutId = R.layout.widget_layout_tiny
}

const val ACTION_WIDGET_SYNC = "com.icanteen.light.ACTION_WIDGET_SYNC"

fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    layoutId: Int
) {
    val views = RemoteViews(context.packageName, layoutId)
    
    var mealNum = "Načítám..."
    var mealName = "Otevřete aplikaci"
    var syncTime = "--:--"
    
    runBlocking {
        val prefs = PreferencesManager(context)
        val hasMenu = prefs.cachedMenuFlow.firstOrNull() != null
        if (hasMenu) {
            val dStore = context.dataStore.data.firstOrNull()
            mealNum = dStore?.get(PreferencesManager.KEY_WIDGET_MEAL_NUMBER) ?: "Oběd"
            mealName = dStore?.get(PreferencesManager.KEY_WIDGET_MEAL_NAME) ?: "Nemáš objednáno"
            syncTime = dStore?.get(PreferencesManager.KEY_WIDGET_LAST_SYNC) ?: "--:--"
        }
    }
    
    val cleanMealNum = mealNum.replace("🥗", "").trim()
    val cleanSyncTime = syncTime.replace("🔄", "").trim()
    
    var finalMealNum = cleanMealNum
    var finalSyncTime = cleanSyncTime
    
    if (layoutId == R.layout.widget_layout) { // Velký
        finalMealNum = "🥗 $cleanMealNum"
        finalSyncTime = "Aktualizováno: $cleanSyncTime"
    } else if (layoutId == R.layout.widget_layout_half) { // Střední
        finalMealNum = "🥗 $cleanMealNum"
        finalSyncTime = "$cleanSyncTime 🔄"
    } else if (layoutId == R.layout.widget_layout_tiny) { // Malý
        finalMealNum = cleanMealNum.replace("Oběd č. ", "")
        if (finalMealNum.length > 2) finalMealNum = "-"
        finalSyncTime = cleanSyncTime
    }

    views.setTextViewText(R.id.widget_meal_number, finalMealNum)
    if (layoutId != R.layout.widget_layout_tiny) {
        views.setTextViewText(R.id.widget_meal_name, mealName)
    }
    views.setTextViewText(R.id.widget_sync_time, finalSyncTime)
    
    // Klik na Root nebo Meal Name otevře appku
    val openAppIntent = Intent(context, MainActivity::class.java)
    val openAppPending = PendingIntent.getActivity(
        context,
        appWidgetId,
        openAppIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    
    // Klik na Sync Time spustí refresh
    val syncIntent = Intent(context, CanteenWidgetProvider::class.java).apply {
        action = ACTION_WIDGET_SYNC
    }
    val syncPending = PendingIntent.getBroadcast(
        context,
        appWidgetId,
        syncIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    
    views.setOnClickPendingIntent(R.id.widget_root, openAppPending)
    if (layoutId == R.layout.widget_layout_tiny) {
        views.setOnClickPendingIntent(R.id.tiny_top_half, openAppPending)
    }
    views.setOnClickPendingIntent(R.id.widget_sync_time, syncPending)
    
    appWidgetManager.updateAppWidget(appWidgetId, views)
}

fun updateAllWidgets(context: Context) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    
    val classes = listOf(
        CanteenWidgetProvider::class.java,
        CanteenWidgetProviderHalf::class.java,
        CanteenWidgetProviderTiny::class.java
    )
    val layouts = listOf(
        R.layout.widget_layout,
        R.layout.widget_layout_half,
        R.layout.widget_layout_tiny
    )
    
    for (i in classes.indices) {
        val componentName = ComponentName(context, classes[i])
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
            for (id in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, id, layouts[i])
            }
        }
    }
}
