package com.icanteen.light.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.icanteen.light.data.CanteenRepository
import com.icanteen.light.data.DayMenu
import com.icanteen.light.data.LunchItem
import com.icanteen.light.data.MenuData
import com.icanteen.light.data.OrderStatus
import com.icanteen.light.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(prefs: PreferencesManager, onNavigateSettings: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()

    var menuData by remember { mutableStateOf<MenuData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showAllDays by remember { mutableStateOf(false) }

    fun loadData() {
        coroutineScope.launch {
            isLoading = true
            val user = prefs.usernameFlow.firstOrNull() ?: ""
            val pass = prefs.passwordFlow.firstOrNull() ?: ""
            val baseUrl = prefs.baseUrlFlow.firstOrNull() ?: "https://stravovani.sspbrno.cz"
            val fetchedData = withContext(Dispatchers.IO) {
                val repo = CanteenRepository(baseUrl)
                val logged = repo.login(user, pass)
                if (logged) repo.fetchMenu() else null
            }
            menuData = fetchedData
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadData() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "iCanteen Light",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (menuData?.userInfo != null) {
                            Text(
                                "${menuData!!.userInfo!!.username}  ·  ${menuData!!.userInfo!!.credit}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { loadData() }) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Aktualizovat",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onNavigateSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Nastavení",
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
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp)
                    )
                    Text("Načítám jídelníček…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (menuData == null || menuData!!.days.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nepodařilo se načíst obědy.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Zkontroluj připojení a přihlašovací údaje.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        } else {
            val days = menuData!!.days
            val today = days.firstOrNull()
            val remainingDays = if (days.isNotEmpty()) days.drop(1) else emptyList()

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (today != null) {
                    item {
                        TodaySection(today)
                        Spacer(modifier = Modifier.height(12.dp))
                        StatisticsPanel(days = days)
                        Spacer(modifier = Modifier.height(24.dp))
                        if (remainingDays.isNotEmpty()) {
                            SectionLabel("Nadcházející dny")
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                val displayedDays = if (showAllDays) remainingDays else remainingDays.take(2)

                items(items = displayedDays, key = { it.dateStr }) { dayMenu ->
                    DayMenuCard(dayMenu)
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (!showAllDays && remainingDays.size > 2) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = { showAllDays = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Zobrazit všechny dostupné dny",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Section label
// ─────────────────────────────────────────────────────────────
@Composable
fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = 1.8.sp,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

// ─────────────────────────────────────────────────────────────
// Today card — premium: single teal accent bar on the left, clean bg
// ─────────────────────────────────────────────────────────────
@Composable
fun TodaySection(dayMenu: DayMenu) {
    val orderedMeal = dayMenu.orderedMeal
    val hasOrder = orderedMeal != null
    val isServed = orderedMeal?.status == OrderStatus.SERVED

    SectionLabel("Dnes · ${dayMenu.dayName} ${dayMenu.dateStr}")
    Spacer(modifier = Modifier.height(10.dp))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (hasOrder && !isServed)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else
            MaterialTheme.colorScheme.surface,
        tonalElevation = if (hasOrder && !isServed) 0.dp else 1.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Accent bar — thicker and taller when ordered
            Box(
                modifier = Modifier
                    .width(if (hasOrder && !isServed) 4.dp else 3.dp)
                    .heightIn(min = 80.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .background(
                        if (hasOrder && !isServed) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                if (hasOrder) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = orderedMeal!!.mealNumber,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isServed) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusChip(if (isServed) "Vydáno" else "Objednáno")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = orderedMeal!!.mealName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isServed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                else MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text = "Na dnešek nemáš objednáno",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Other options today
    val otherMeals = if (orderedMeal != null)
        dayMenu.meals.filter { it != orderedMeal }
    else
        dayMenu.meals

    if (otherMeals.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        SectionLabel("Ostatní možnosti dnes")
        Spacer(modifier = Modifier.height(6.dp))
        otherMeals.forEach { meal ->
            MealRow(meal)
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Day card — subtle surface, no colored background
// ─────────────────────────────────────────────────────────────
@Composable
fun DayMenuCard(dayMenu: DayMenu) {
    val isUrgent = dayMenu.orderedMeal == null && dayMenu.meals.isNotEmpty()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dayMenu.dayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = dayMenu.dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isUrgent) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.errorContainer,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Neobjednáno",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant,
                thickness = 0.5.dp
            )

            // Meals list
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                dayMenu.meals.forEach { meal ->
                    MealRow(meal)
                    Spacer(modifier = Modifier.height(3.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Meal row — no colored background per row; only indicator dots
// ─────────────────────────────────────────────────────────────
@Composable
fun MealRow(meal: LunchItem) {
    val isOrdered = meal.status == OrderStatus.ORDERED || meal.status == OrderStatus.ORDERED_LOCKED
    val isAvailable = meal.status == OrderStatus.AVAILABLE
    val isLocked = meal.status == OrderStatus.LOCKED || meal.status == OrderStatus.NOT_AVAILABLE

    val indicatorColor = when {
        isOrdered   -> MaterialTheme.colorScheme.primary
        isAvailable -> MaterialTheme.colorScheme.secondary
        else        -> MaterialTheme.colorScheme.surfaceVariant
    }

    val mealNameColor = when {
        isOrdered -> MaterialTheme.colorScheme.onSurface
        isLocked  -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        else      -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Color dot indicator
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(indicatorColor, RoundedCornerShape(50))
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = meal.mealNumber,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = if (isOrdered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.width(36.dp)
        )

        Text(
            text = meal.mealName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isOrdered) FontWeight.Medium else FontWeight.Normal,
            color = mealNameColor,
            modifier = Modifier.weight(1f)
        )

        if (isOrdered) {
            Spacer(modifier = Modifier.width(8.dp))
            StatusChip(if (meal.status == OrderStatus.ORDERED_LOCKED) "Uzamčeno" else "Objednáno")
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Small status chip — no pill shapes, subtle tonal badge
// ─────────────────────────────────────────────────────────────
@Composable
fun StatusChip(label: String, isAlert: Boolean = false) {
    val bg = if (isAlert) MaterialTheme.colorScheme.errorContainer
             else MaterialTheme.colorScheme.primaryContainer
    val fg = if (isAlert) MaterialTheme.colorScheme.onErrorContainer
             else MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.Medium)
    }
}

// ─────────────────────────────────────────────────────────────
// Statistics panel — clean numbers, no loud background
// ─────────────────────────────────────────────────────────────
@Composable
fun StatisticsPanel(days: List<DayMenu>) {
    // Cache formatters — SimpleDateFormat construction is expensive
    val currentDateStr = remember {
        java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale("cs", "CZ")).format(java.util.Date())
    }
    val shortDateStr = remember {
        java.text.SimpleDateFormat("d. M.", java.util.Locale("cs", "CZ")).format(java.util.Date())
    }

    // Cache expensive index calculations — only recalculates when `days` changes
    val totalDays = remember(days) { days.size }
    val orderedDays = remember(days) { days.count { it.orderedMeal != null } }
    val missingDays = remember(days) { totalDays - orderedDays }

    val missingIndices = remember(days, currentDateStr, shortDateStr) {
        days.indices.filter {
            val isToday = days[it].dateStr == currentDateStr || days[it].dateStr.contains(shortDateStr)
            days[it].orderedMeal == null && days[it].meals.isNotEmpty() && !isToday
        }
    }

    val urgent = remember(missingIndices) { missingIndices.isNotEmpty() && missingIndices[0] <= 2 }

    val missingLines = mutableListOf<String>()
    fun buildText(index: Int): String {
        val missingDay = days[index]
        val isTomorrow = index == 1
        val daysDesc = when {
            isTomorrow -> " · Zítra"
            index == 2 -> " · Pozítří"
            else -> ""
        }
        return "${missingDay.dayName} ${missingDay.dateStr}$daysDesc"
    }

    if (missingIndices.isNotEmpty()) {
        if (urgent) {
            missingLines.add(buildText(missingIndices[0]))
            if (missingIndices.size > 1) missingLines.add(buildText(missingIndices[1]))
        } else {
            missingLines.add(buildText(missingIndices[0]))
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {

            // ── Alert / Info band — PRIMARY content, shown first ──────
            if (missingLines.isNotEmpty()) {
                if (urgent) {
                    // Urgent: full highlighted block at top
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.error, RoundedCornerShape(3.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text("!", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onError,
                                fontWeight = FontWeight.Black)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "Chybí objednávka",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                            missingLines.forEach {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                } else {
                    // Non-urgent: subtle note
                    Text(
                        "Nejbližší den bez oběda: ${missingLines[0]}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(10.dp))
            }

            // ── Stats row — secondary, uniform small size ─────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatBox(
                    value = "$missingDays",
                    label = "Neobjednáno",
                    modifier = Modifier.weight(1f),
                    accentColor = if (missingDays > 0) MaterialTheme.colorScheme.error
                                  else MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatBox(
                    value = "$orderedDays",
                    label = "Objednáno",
                    modifier = Modifier.weight(1f),
                    accentColor = MaterialTheme.colorScheme.primary
                )
                StatBox(
                    value = "$totalDays",
                    label = "Celkem dnů",
                    modifier = Modifier.weight(1f),
                    accentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StatBox(value: String, label: String, modifier: Modifier = Modifier, accentColor: Color) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
