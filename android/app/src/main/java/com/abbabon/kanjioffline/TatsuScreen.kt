package com.abbabon.kanjioffline

import android.content.ClipData
import android.os.Build
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
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
    val twoPane = navigator.scaffoldDirective.maxHorizontalPartitions > 1

    val labels = Labels(
        strokes = stringResource(R.string.meta_strokes),
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

    // launch: focus the search field (it is not composed if a restored detail hides the list)
    LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
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
                        onSearchFocus = {},
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
