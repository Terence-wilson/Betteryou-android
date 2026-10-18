@file:OptIn(ExperimentalMaterial3Api::class)

package com.betteryou.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betteryou.app.domain.AppSettings
import com.betteryou.app.domain.today
import com.betteryou.app.notifications.NotificationRoute
import com.betteryou.app.ui.checkin.CheckInScreen
import com.betteryou.app.ui.dashboard.DashboardScreen
import com.betteryou.app.ui.dashboard.FocusEditorScreen
import com.betteryou.app.ui.dashboard.MoodEntrySheet
import com.betteryou.app.ui.lists.ListsScreen
import com.betteryou.app.ui.metrics.MetricsScreen
import com.betteryou.app.ui.onboarding.OnboardingScreen
import com.betteryou.app.ui.settings.SettingsScreen

private enum class MainTab(val label: String, val title: String, val icon: ImageVector) {
    TODAY("Today", "BetterYou", Icons.Filled.WbSunny),
    LISTS("Lists", "My Lists", Icons.Filled.List),
    PROGRESS("Progress", "Progress", Icons.Filled.Insights),
    SETTINGS("Settings", "Settings", Icons.Filled.Settings),
}

/** Full-screen flows shown on top of the tabs. */
private enum class Overlay { CHECK_IN, FOCUS_EDITOR }

@Composable
fun BetterYouApp(
    pendingRoute: NotificationRoute?,
    onRouteHandled: () -> Unit,
    viewModel: AppViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.settings
    when {
        settings == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        !settings.hasCompletedOnboarding -> OnboardingScreen(
            settings = settings,
            items = state.items,
            onUpdateSettings = viewModel::updateSettings,
            onAddItem = viewModel::addItem,
            onRenameItem = viewModel::renameItem,
            onDeleteItem = viewModel::deleteItem,
            nameConflict = viewModel::nameConflict,
            onFinish = viewModel::finishOnboarding,
        )
        else -> MainScreen(state, settings, viewModel, pendingRoute, onRouteHandled)
    }
}

@Composable
private fun MainScreen(
    state: AppState,
    settings: AppSettings,
    viewModel: AppViewModel,
    pendingRoute: NotificationRoute?,
    onRouteHandled: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(MainTab.TODAY) }
    var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
    var showMood by rememberSaveable { mutableStateOf(false) }
    val cycle = state.activeCycle
    val snackbarHostState = remember { SnackbarHostState() }

    // Offer Undo after deleting a list item.
    LaunchedEffect(viewModel) {
        viewModel.deletedItems.collect { deleted ->
            val result = snackbarHostState.showSnackbar(
                message = "Deleted “${deleted.item.name}”",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreItem(deleted)
        }
    }

    // Tapping a notification opens the check-in or the daily mood sheet.
    LaunchedEffect(pendingRoute) {
        when (pendingRoute) {
            NotificationRoute.CHECK_IN -> if (cycle != null) overlay = Overlay.CHECK_IN
            NotificationRoute.DAILY_MOOD -> showMood = true
            null -> return@LaunchedEffect
        }
        onRouteHandled()
    }
    LaunchedEffect(overlay, cycle) {
        if (overlay != null && cycle == null) overlay = null
    }

    when {
        overlay == Overlay.CHECK_IN && cycle != null -> CheckInScreen(
            cycle = cycle,
            settings = settings,
            items = state.items,
            habitLogs = state.habitLogs,
            onAddItem = viewModel::addItem,
            onComplete = { scores, adherence, note, nextFocus ->
                viewModel.completeCheckIn(cycle, scores, adherence, note, nextFocus)
                overlay = null
                tab = MainTab.TODAY
            },
            onCancel = { overlay = null },
        )

        overlay == Overlay.FOCUS_EDITOR && cycle != null -> FocusEditorScreen(
            cycle = cycle,
            items = state.items,
            onAddItem = viewModel::addItem,
            onSave = {
                viewModel.updateFocus(cycle, it)
                overlay = null
            },
            onCancel = { overlay = null },
        )

        else -> Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(tab.title) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
            bottomBar = {
                NavigationBar {
                    MainTab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            icon = { Icon(entry.icon, contentDescription = null) },
                            label = { Text(entry.label) },
                        )
                    }
                }
            },
        ) { padding ->
            when (tab) {
                MainTab.TODAY -> DashboardScreen(
                    cycle = cycle,
                    items = state.items,
                    habitLogs = state.habitLogs,
                    dailyCheckInEnabled = settings.dailyCheckInEnabled,
                    todaysMood = state.todaysMood,
                    onStartCheckIn = { overlay = Overlay.CHECK_IN },
                    onEditFocus = { overlay = Overlay.FOCUS_EDITOR },
                    onOpenMood = { showMood = true },
                    onStartCycle = viewModel::startCycle,
                    onSetHabitKept = viewModel::setHabitKept,
                    contentPadding = padding,
                )
                MainTab.LISTS -> ListsScreen(
                    items = state.items,
                    onAdd = viewModel::addItem,
                    onRename = viewModel::renameItem,
                    onDelete = viewModel::deleteItem,
                    nameConflict = viewModel::nameConflict,
                    contentPadding = padding,
                )
                MainTab.PROGRESS -> MetricsScreen(
                    checkIns = state.checkIns,
                    moods = state.moods,
                    items = state.items,
                    onUpdateCheckInNote = viewModel::updateCheckInNote,
                    onDeleteCheckIn = viewModel::deleteCheckIn,
                    onSaveMood = viewModel::saveMood,
                    onDeleteMood = viewModel::deleteMood,
                    contentPadding = padding,
                )
                MainTab.SETTINGS -> SettingsScreen(
                    settings = settings,
                    activeCycle = cycle,
                    onUpdate = viewModel::updateSettings,
                    contentPadding = padding,
                )
            }
        }
    }

    if (showMood) {
        MoodEntrySheet(
            existing = state.todaysMood,
            onSave = { mood, note ->
                viewModel.saveMood(today(), mood, note)
                showMood = false
            },
            onDismiss = { showMood = false },
        )
    }
}
