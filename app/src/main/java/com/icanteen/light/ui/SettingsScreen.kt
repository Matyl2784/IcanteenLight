package com.icanteen.light.ui

import android.app.TimePickerDialog
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.icanteen.light.data.PreferencesManager
import com.icanteen.light.worker.AlarmScheduler
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

fun showTimePicker(context: Context, initialTime: String, onTimeSelected: (String) -> Unit) {
    val parts = initialTime.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 12
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    TimePickerDialog(context, { _, h, m ->
        onTimeSelected(String.format("%02d:%02d", h, m))
    }, hour, minute, true).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(prefs: PreferencesManager, onLogout: () -> Unit, onBack: () -> Unit) {
    var baseUrl by remember { mutableStateOf("") }
    var selectedDarkMode by remember { mutableStateOf("Systémový") }
    val coroutineScope = rememberCoroutineScope()

    var isDarkModeDropdownExpanded by remember { mutableStateOf(false) }
    val darkModes = listOf("Systémový", "Světlý", "Tmavý")

    var warningEnabled by remember { mutableStateOf(false) }
    var warningTime by remember { mutableStateOf("18:00") }
    var warningDays by remember { mutableStateOf(2) }

    var dailyEnabled by remember { mutableStateOf(false) }
    var dailyTime by remember { mutableStateOf("11:30") }
    var newMealsEnabled by remember { mutableStateOf(false) }
    
    var widgetUpdateTime by remember { mutableStateOf("03:00") }

    val context = LocalContext.current
    var permissionRequested by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (!isGranted) {
                warningEnabled = false
                dailyEnabled = false
                newMealsEnabled = false
            }
        }
    )

    fun requestPermissionsIfNeeded() {
        if (!permissionRequested && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionRequested = true
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        baseUrl = prefs.baseUrlFlow.firstOrNull() ?: "https://stravovani.sspbrno.cz"
        selectedDarkMode = prefs.darkModeFlow.firstOrNull() ?: "Systémový"
        warningEnabled = prefs.notifWarningEnabledFlow.firstOrNull() ?: false
        warningTime = prefs.notifWarningTimeFlow.firstOrNull() ?: "18:00"
        warningDays = prefs.notifWarningDaysFlow.firstOrNull() ?: 2
        dailyEnabled = prefs.notifDailyEnabledFlow.firstOrNull() ?: false
        dailyTime = prefs.notifDailyTimeFlow.firstOrNull() ?: "11:30"
        newMealsEnabled = prefs.notifNewMealsEnabledFlow.firstOrNull() ?: false
        widgetUpdateTime = prefs.widgetUpdateTimeFlow.firstOrNull() ?: "03:00"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Nastavení",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zpět",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {

            // ── Server ──────────────────────────────────────────────────
            SettingsSection("Server") {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Adresa iCanteen") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                PrimaryButton("Uložit adresu") {
                    coroutineScope.launch {
                        prefs.saveBaseUrl(baseUrl)
                        onBack()
                    }
                }
            }

            SettingsDivider()
            
            // ── Widgety ──────────────────────────────────────────────────
            SettingsSection("Widgety") {
                Text(
                    "Čas, kdy se má na pozadí stáhnout a aktualizovat oběd ve widgetech.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                
                SettingsTimeRow("Čas automatické aktualizace", widgetUpdateTime) {
                    showTimePicker(context, widgetUpdateTime) { 
                        widgetUpdateTime = it
                        coroutineScope.launch {
                            prefs.saveWidgetUpdateTime(it)
                            AlarmScheduler.scheduleAlarms(context)
                        }
                    }
                }
            }

            SettingsDivider()

            // ── Vzhled ──────────────────────────────────────────────────
            SettingsSection("Vzhled") {
                ExposedDropdownMenuBox(
                    expanded = isDarkModeDropdownExpanded,
                    onExpandedChange = { isDarkModeDropdownExpanded = !isDarkModeDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedDarkMode,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tmavý režim") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDarkModeDropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = isDarkModeDropdownExpanded,
                        onDismissRequest = { isDarkModeDropdownExpanded = false }
                    ) {
                        darkModes.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode, style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    selectedDarkMode = mode
                                    isDarkModeDropdownExpanded = false
                                    coroutineScope.launch { prefs.saveDarkMode(mode) }
                                }
                            )
                        }
                    }
                }
            }

            SettingsDivider()

            // ── Notifikace ──────────────────────────────────────────────
            SettingsSection("Upozornění") {
                Text(
                    "Na některých telefonech se mohou notifikace o pár minut opozdit z důvodu úspory baterie.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Warning notif
                SettingsToggleRow(
                    label = "Varování na chybějící oběd",
                    checked = warningEnabled,
                    onCheckedChange = { warningEnabled = it; requestPermissionsIfNeeded() }
                )
                if (warningEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsTimeRow("Čas upozornění", warningTime) {
                        showTimePicker(context, warningTime) { warningTime = it }
                    }
                    SettingsCounterRow(
                        label = "Dní předem",
                        value = warningDays,
                        onDecrement = { if (warningDays > 1) warningDays-- },
                        onIncrement = { if (warningDays < 5) warningDays++ }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Daily notif
                SettingsToggleRow(
                    label = "Denní info (dnešní oběd)",
                    checked = dailyEnabled,
                    onCheckedChange = { dailyEnabled = it; requestPermissionsIfNeeded() }
                )
                if (dailyEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsTimeRow("Čas upozornění", dailyTime) {
                        showTimePicker(context, dailyTime) { dailyTime = it }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // New meals in system notif
                SettingsToggleRow(
                    label = "Nové obědy v systému",
                    checked = newMealsEnabled,
                    onCheckedChange = { newMealsEnabled = it; requestPermissionsIfNeeded() }
                )

                Spacer(modifier = Modifier.height(14.dp))
                PrimaryButton("Uložit notifikace") {
                    coroutineScope.launch {
                        prefs.saveNotifWarning(warningEnabled, warningTime, warningDays)
                        prefs.saveNotifDaily(dailyEnabled, dailyTime)
                        prefs.saveNotifNewMeals(newMealsEnabled)
                        AlarmScheduler.scheduleAlarms(context)
                        Toast.makeText(context, "✓ Notifikace uloženy", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            SettingsDivider()

            // ── Účet ────────────────────────────────────────────────────
            SettingsSection("Účet") {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            prefs.clearCredentials()
                            onLogout()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        )
                    )
                ) {
                    Text("Odhlásit se a vymazat data", fontWeight = FontWeight.Medium)
                }
            }

            // Footer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "iCanteen Light  ·  v1.0",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Reusable Settings building blocks
// ─────────────────────────────────────────────────────────────

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.surfaceVariant,
        thickness = 0.5.dp,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
fun SettingsToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
fun SettingsTimeRow(label: String, time: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onClick) {
            Text(time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun SettingsCounterRow(label: String, value: Int, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDecrement) {
                Text("−", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "$value",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(20.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            TextButton(onClick = onIncrement) {
                Text("+", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun PrimaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        Text(label, fontWeight = FontWeight.Medium)
    }
}
