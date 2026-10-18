package com.betteryou.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.betteryou.app.BetterYouApplication
import com.betteryou.app.data.DeletedItem
import com.betteryou.app.domain.AppSettings
import com.betteryou.app.domain.CheckIn
import com.betteryou.app.domain.Cycle
import com.betteryou.app.domain.HabitLog
import com.betteryou.app.domain.HobbyScores
import com.betteryou.app.domain.ItemKind
import com.betteryou.app.domain.MoodEntry
import com.betteryou.app.domain.TrackedItem
import com.betteryou.app.domain.today
import com.betteryou.app.notifications.Reminders
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

data class AppState(
    val loaded: Boolean = false,
    val settings: AppSettings? = null,
    val items: List<TrackedItem> = emptyList(),
    val activeCycle: Cycle? = null,
    /** Oldest first. */
    val checkIns: List<CheckIn> = emptyList(),
    /** Oldest first. */
    val moods: List<MoodEntry> = emptyList(),
    val habitLogs: List<HabitLog> = emptyList(),
) {
    val todaysMood: MoodEntry? get() = moods.lastOrNull { it.day == today() }

    fun items(kind: ItemKind): List<TrackedItem> = items.filter { it.kind == kind }
}

/** Result of trying to add an item, so the UI can explain why nothing new appeared. */
sealed interface AddResult {
    val id: String

    data class Added(override val id: String) : AddResult
    data class AlreadyExists(override val id: String, val name: String) : AddResult
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as BetterYouApplication).repository

    val state: StateFlow<AppState> = combine(
        combine(
            repository.settings,
            repository.items,
            repository.activeCycle,
            repository.checkIns,
            repository.moods,
        ) { settings, items, cycle, checkIns, moods ->
            AppState(true, settings, items, cycle, checkIns, moods)
        },
        repository.habitLogs,
    ) { state, logs -> state.copy(habitLogs = logs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState())

    /** Items just deleted, so the UI can offer Undo. */
    private val _deletedItems = MutableSharedFlow<DeletedItem>(extraBufferCapacity = 1)
    val deletedItems: SharedFlow<DeletedItem> = _deletedItems

    init {
        viewModelScope.launch { repository.ensureSettings() }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.updateSettings(transform) }
    }

    fun finishOnboarding(focus: Set<String>) {
        viewModelScope.launch { repository.finishOnboarding(focus) }
    }

    /** Adds an item unless one with the same name already exists in that list. */
    fun addItem(name: String, kind: ItemKind): AddResult {
        val trimmed = name.trim()
        state.value.items(kind).firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let {
            return AddResult.AlreadyExists(it.id, it.name)
        }
        val id = UUID.randomUUID().toString()
        viewModelScope.launch { repository.addItem(id, trimmed, kind) }
        return AddResult.Added(id)
    }

    /** Name another item of the same kind already uses, or null if [name] is free for [item]. */
    fun nameConflict(item: TrackedItem, name: String): String? {
        val trimmed = name.trim()
        return state.value.items(item.kind)
            .firstOrNull { it.id != item.id && it.name.equals(trimmed, ignoreCase = true) }
            ?.name
    }

    fun renameItem(item: TrackedItem, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || nameConflict(item, trimmed) != null) return
        viewModelScope.launch { repository.renameItem(item.id, trimmed) }
    }

    fun deleteItem(item: TrackedItem) {
        viewModelScope.launch { _deletedItems.emit(repository.deleteItem(item)) }
    }

    fun restoreItem(deleted: DeletedItem) {
        viewModelScope.launch { repository.restoreItem(deleted) }
    }

    fun startCycle() {
        viewModelScope.launch { repository.startCycle(focus = emptySet()) }
    }

    fun updateFocus(cycle: Cycle, focus: Set<String>) {
        viewModelScope.launch { repository.updateFocus(cycle.id, focus) }
    }

    fun setHabitKept(itemId: String, kept: Boolean) {
        viewModelScope.launch { repository.setHabitKept(itemId, today(), kept) }
    }

    fun completeCheckIn(
        cycle: Cycle,
        hobbyScores: Map<String, HobbyScores>,
        adherence: Map<String, Int?>,
        note: String,
        nextFocus: Set<String>,
    ) {
        viewModelScope.launch { repository.completeCheckIn(cycle.id, hobbyScores, adherence, note, nextFocus) }
    }

    fun updateCheckInNote(checkIn: CheckIn, note: String) {
        viewModelScope.launch { repository.updateCheckInNote(checkIn.id, note) }
    }

    fun deleteCheckIn(checkIn: CheckIn) {
        viewModelScope.launch { repository.deleteCheckIn(checkIn.id) }
    }

    fun saveMood(day: LocalDate, mood: Int, note: String) {
        viewModelScope.launch { repository.saveMood(day, mood, note) }
        if (day == today()) Reminders.clearDailyNotification(getApplication())
    }

    fun deleteMood(day: LocalDate) {
        viewModelScope.launch { repository.deleteMood(day) }
    }
}
