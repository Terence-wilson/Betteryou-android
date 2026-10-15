package com.betteryou.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.betteryou.app.BetterYouApplication
import com.betteryou.app.domain.AppSettings
import com.betteryou.app.domain.CheckIn
import com.betteryou.app.domain.Cycle
import com.betteryou.app.domain.HobbyScores
import com.betteryou.app.domain.ItemKind
import com.betteryou.app.domain.MoodEntry
import com.betteryou.app.domain.TrackedItem
import com.betteryou.app.domain.today
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
) {
    val todaysMood: MoodEntry? get() = moods.lastOrNull { it.day == today() }

    fun items(kind: ItemKind): List<TrackedItem> = items.filter { it.kind == kind }
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as BetterYouApplication).repository

    val state: StateFlow<AppState> = combine(
        repository.settings,
        repository.items,
        repository.activeCycle,
        repository.checkIns,
        repository.moods,
    ) { settings, items, cycle, checkIns, moods ->
        AppState(true, settings, items, cycle, checkIns, moods)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState())

    init {
        viewModelScope.launch { repository.ensureSettings() }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.updateSettings(transform) }
    }

    fun finishOnboarding(focus: Set<String>) {
        viewModelScope.launch { repository.finishOnboarding(focus) }
    }

    /** Adds an item unless one with the same name already exists. Returns the ID of the new or existing item. */
    fun addItem(name: String, kind: ItemKind): String {
        val trimmed = name.trim()
        state.value.items(kind).firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it.id }
        val id = UUID.randomUUID().toString()
        viewModelScope.launch { repository.addItem(id, trimmed, kind) }
        return id
    }

    fun renameItem(item: TrackedItem, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.renameItem(item.id, trimmed) }
    }

    fun deleteItem(item: TrackedItem) {
        viewModelScope.launch { repository.deleteItem(item.id) }
    }

    fun startCycle() {
        viewModelScope.launch { repository.startCycle(focus = emptySet()) }
    }

    fun updateFocus(cycle: Cycle, focus: Set<String>) {
        viewModelScope.launch { repository.updateFocus(cycle.id, focus) }
    }

    fun completeCheckIn(
        cycle: Cycle,
        hobbyScores: Map<String, HobbyScores>,
        adherence: Map<String, Int>,
        note: String,
        nextFocus: Set<String>,
    ) {
        viewModelScope.launch { repository.completeCheckIn(cycle, hobbyScores, adherence, note, nextFocus) }
    }

    fun saveMood(mood: Int, note: String) {
        viewModelScope.launch { repository.saveMood(today(), mood, note) }
    }
}
