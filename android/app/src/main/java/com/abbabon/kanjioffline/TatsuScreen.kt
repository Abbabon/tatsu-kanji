package com.abbabon.kanjioffline

import android.content.ClipData
import android.os.Build
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun TatsuScreen(vm: TatsuViewModel) {
    val query by vm.query.collectAsState()
    val found by vm.found.collectAsState()
    val ready by vm.ready.collectAsState()
    val selected by vm.selected.collectAsState()
    val recentOrder by vm.recentOrder.collectAsState()
    // `ready` is read here (outer scope) and keys both remembers: the searcher loads after the DataStore emits,
    // and `vm.kanji` is not observable, so without it Recent and a restored detail would stay empty until the next change.
    // Recents whose kanji vanished after a data update are skipped.
    val recents = remember(recentOrder, ready) { recentOrder.mapNotNull { vm.kanji(it) } }
    val selectedKanji = remember(selected, ready) { selected?.let { vm.kanji(it) } }

    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboard.current
    val searchFocus = remember { FocusRequester() }
    var showAbout by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    var rootFocused by remember { mutableStateOf(false) }   // any node under the scaffold has focus
    val twoPane = navigator.scaffoldDirective.maxHorizontalPartitions > 1

    val res = LocalContext.current.resources
    val labels = Labels(
        strokes = { n -> res.getQuantityString(R.plurals.meta_strokes, n, n.toString()) },  // %s: ASCII digits in every locale
        grade = stringResource(R.string.meta_grade),
        joyo = stringResource(R.string.meta_joyo),
        jinmei = stringResource(R.string.meta_jinmei),
        jlpt = stringResource(R.string.meta_jlpt),
        freq = stringResource(R.string.meta_freq),
    )
    val copiedText = stringResource(R.string.copied)

    fun open(k: String) {
        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, k) }
    }

    fun pick(k: String) {
        vm.pick(k)
        open(k)
    }

    fun enter() {
        vm.enter()?.let { open(it) }
    }

    fun copy(k: String) {
        vm.touch(k)
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("kanji", k)))
            // Android 13+ shows its own clipboard preview; older versions get a snackbar
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) snackbar.showSnackbar(copiedText)
        }
    }

    // launch, and whenever the list pane becomes visible again (back from detail on a phone): focus the search field.
    // While the list is hidden its field is disposed, so the flag is reset (onFocusChanged does not fire on disposal).
    val listVisible = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] != PaneAdaptedValue.Hidden
    LaunchedEffect(listVisible) {
        if (listVisible) {
            withFrameNanos { }   // let the pane compose first
            runCatching { searchFocus.requestFocus() }
        } else searchFocused = false
    }

    // one-shot VM event (never `query`, which would also fire after rotation): phone showing a detail -> back to the list
    LaunchedEffect(Unit) {
        vm.handOffs.collect {
            if (navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Hidden) navigator.navigateBack()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .onFocusChanged { rootFocused = it.hasFocus }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // a focused button (Copy, Clear, back, a row) must still get its own Enter and Space
                val otherFocused = rootFocused && !searchFocused
                when {
                    e.isCtrlPressed && e.key == Key.F -> {
                        runCatching { searchFocus.requestFocus() }
                        true
                    }
                    e.isCtrlPressed || e.isMetaPressed || e.isAltPressed -> false
                    e.key == Key.DirectionDown -> { vm.step(1); true }
                    e.key == Key.DirectionUp -> { vm.step(-1); true }
                    (e.key == Key.Enter || e.key == Key.NumPadEnter) && !otherFocused -> { enter(); true }
                    otherFocused && (e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.Spacebar) -> false
                    e.key == Key.Backspace ->
                        if (!searchFocused && vm.query.value.isNotEmpty()) {
                            vm.setQuery(vm.query.value.dropLastCodePoint())
                            runCatching { searchFocus.requestFocus() }
                            true
                        } else false
                    !searchFocused && e.utf16CodePoint > 0 -> {
                        // type anywhere: forward printable keys to the search field
                        val s = String(Character.toChars(e.utf16CodePoint))
                        if (isTypable(s)) {
                            vm.setQuery(vm.query.value + s)
                            runCatching { searchFocus.requestFocus() }
                            true
                        } else false
                    }
                    else -> false
                }
            },
        // the outer Scaffold has no window insets, so keep the snackbar clear of the nav bar and keyboard
        snackbarHost = { SnackbarHost(snackbar, Modifier.windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            modifier = Modifier.padding(padding),
            listPane = {
                AnimatedPane {
                    ListPane(
                        query = query,
                        ready = ready,
                        found = found,
                        recents = recents,
                        selected = selected,
                        highlight = twoPane,
                        searchFocus = searchFocus,
                        onSearchFocus = { searchFocused = it },
                        onQuery = vm::setQuery,
                        onPick = ::pick,
                        onEnter = ::enter,
                        onCopy = ::copy,
                        onRemove = vm::remove,
                        onClearRecents = {
                            vm.clearRecents()
                            runCatching { searchFocus.requestFocus() }
                        },
                        onAbout = { showAbout = true },
                    )
                }
            },
            detailPane = {
                AnimatedPane {
                    val e = selectedKanji
                    if (e != null) {
                        DetailPane(
                            kanji = e,
                            labels = labels,
                            showBack = !twoPane,
                            onBack = { scope.launch { navigator.navigateBack() } },
                            onCopy = { copy(e.k) },
                        )
                    } else {
                        EmptyDetail()
                    }
                }
            },
        )
    }

    if (showAbout) AboutSheet(onDismiss = { showAbout = false })
}

/** Removes the last code point (so a non-BMP kanji is not left as half a surrogate pair). */
private fun String.dropLastCodePoint(): String =
    if (isEmpty()) this else substring(0, offsetByCodePoints(length, -1))
