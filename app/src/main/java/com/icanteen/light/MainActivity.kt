package com.icanteen.light


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.icanteen.light.data.PreferencesManager
import com.icanteen.light.ui.LoginScreen
import com.icanteen.light.ui.MenuScreen
import com.icanteen.light.ui.SettingsScreen
import com.icanteen.light.worker.MenuUpdateWorker
import com.icanteen.light.ui.theme.CanteenTheme
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupBackgroundWork()

        val prefs = PreferencesManager(this)
        
        setContent {
            val darkMode by prefs.darkModeFlow.collectAsState(initial = "Systémový")
            CanteenTheme(darkMode = darkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isLoggedIn by prefs.isLoggedInFlow.collectAsState(initial = false)
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = if (isLoggedIn) "menu" else "login"
                    ) {
                        composable("login") {
                            LoginScreen(prefs, onLoginSuccess = {
                                navController.navigate("menu") {
                                    popUpTo("login") { inclusive = true }
                                }
                            })
                        }
                        composable("menu") {
                            MenuScreen(prefs, onNavigateSettings = {
                                navController.navigate("settings")
                            })
                        }
                        composable("settings") {
                            SettingsScreen(prefs, onLogout = {
                                navController.navigate("login") {
                                    popUpTo(0)
                                }
                            }, onBack = {
                                navController.popBackStack()
                            })
                        }
                    }
                }
            }
        }
    }

    private fun setupBackgroundWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val currentDate = Calendar.getInstance()
        val dueDate = Calendar.getInstance()
        
        dueDate.set(Calendar.HOUR_OF_DAY, 3)
        dueDate.set(Calendar.MINUTE, 0)
        dueDate.set(Calendar.SECOND, 0)
        
        if (dueDate.before(currentDate)) {
            dueDate.add(Calendar.HOUR_OF_DAY, 24)
        }
        
        val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis
        
        val dailyWorkRequest = PeriodicWorkRequestBuilder<MenuUpdateWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()
            
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "CanteenMenuUpdate",
            ExistingPeriodicWorkPolicy.UPDATE,
            dailyWorkRequest
        )
    }
}
