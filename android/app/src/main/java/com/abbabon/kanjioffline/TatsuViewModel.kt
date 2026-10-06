package com.abbabon.kanjioffline

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Results and the query they belong to. */
data class Found(val query: String, val kanji: List<Kanji>)

class TatsuViewModel(app: Application, private val handle: SavedStateHandle) : AndroidViewModel(app) {
    private val recents = Recents(app.recentsStore)
    private val searcher = MutableStateFlow<Searcher?>(null)

    /** Survives process death through SavedStateHandle. */
    val query: StateFlow<String> = handle.getStateFlow("query", "")
    val selected: StateFlow<String?> = handle.getStateFlow<String?>("selected", null)

    val ready: StateFlow<Boolean> =
        searcher.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Every new query cancels the search still running for the previous one. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val found: StateFlow<Found> = combine(query, searcher.filterNotNull()) { q, s -> q to s }   // no results (and no "No matches.") until the searcher is loaded
        .mapLatest { (q, s) -> Found(q, s.search(q)) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Found("", emptyList()))

    // Recent order shown: frozen while browsing so a tap doesn't re-sort under the finger,
    // refreshed whenever the recents are shown again (query cleared).
    private var latestRecents: List<String> = emptyList()
    private var firstRecents = true
    private val _recentOrder = MutableStateFlow<List<String>>(emptyList())
    val recentOrder: StateFlow<List<String>> = _recentOrder.asStateFlow()

    init {
        // A failure to decode kanji.json is a build bug (the asset ships in the APK): crash, like fatalError on iOS.
        viewModelScope.launch(Dispatchers.Default) {
            searcher.value = Searcher(getApplication<Application>().loadKanji())
        }
        viewModelScope.launch {
            recents.flow.collect { latest ->
                latestRecents = latest
                _recentOrder.value = if (firstRecents) latest else mergeOrder(_recentOrder.value, latest)
                firstRecents = false
            }
        }
    }

    fun kanji(k: String): Kanji? = searcher.value?.kanji(k)

    /** One event per text-selection hand-off (Task 11). No replay, so a rotation or a new collector never sees an old one. */
    private val _handOffs = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val handOffs: SharedFlow<Unit> = _handOffs

    fun applyHandOff(text: String) {
        setQuery(text)
        _handOffs.tryEmit(Unit)
    }

    fun setQuery(q: String) {
        val becameBlank = q.isBlank() && query.value.isNotBlank()
        handle["query"] = q
        if (becameBlank) _recentOrder.value = latestRecents
    }

    /** Ids that Up/Down and Enter act on: the recents when the query is blank, else the results. */
    fun visibleIds(): List<String> =
        if (query.value.isBlank()) _recentOrder.value.filter { kanji(it) != null } else found.value.kanji.map { it.k }

    /** An explicit pick (tap or Enter): select and record. */
    fun pick(k: String) {
        handle["selected"] = k
        touch(k)
    }

    /** Record a lookup (also used by Copy). */
    fun touch(k: String) {
        viewModelScope.launch { recents.touch(k) }
    }

    /** Enter: record the current selection, or pick (and record) the first visible result. Returns what was picked. */
    fun enter(): String? {
        val k = selected.value ?: visibleIds().firstOrNull() ?: return null
        pick(k)
        return k
    }

    /** Arrow keys: move the selection only, never record. */
    fun step(delta: Int) {
        handle["selected"] = stepSelection(selected.value, visibleIds(), delta)
    }

    fun remove(k: String) {
        viewModelScope.launch { recents.remove(k) }
        if (selected.value == k) handle["selected"] = null
    }

    fun clearRecents() {
        viewModelScope.launch { recents.clear() }
        handle["selected"] = null
    }
}
