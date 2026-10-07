package com.abbabon.kanjioffline

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListPane(
    query: String,
    ready: Boolean,
    found: Found,
    recents: List<Kanji>,
    selected: String?,
    highlight: Boolean,                 // highlight the selected row (two-pane mode)
    searchFocus: FocusRequester,
    onSearchFocus: (Boolean) -> Unit,
    onQuery: (String) -> Unit,
    onPick: (String) -> Unit,
    onEnter: () -> Unit,
    onCopy: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearRecents: () -> Unit,
    onAbout: () -> Unit,
) {
    val blank = query.isBlank()
    val rows = if (blank) recents else found.kanji
    val headerOffset = if (blank && recents.isNotEmpty()) 1 else 0
    val listState = rememberLazyListState()

    // a new result set starts at the top: the keyed list would otherwise stay anchored to the old first row and
    // scroll the new best match out of view
    LaunchedEffect(found, blank) { listState.scrollToItem(0) }

    // keep the keyboard-selected row on screen (keyed on the selection only, so new results don't pull the list back down)
    LaunchedEffect(selected) {
        val i = rows.indexOfFirst { it.k == selected }
        if (i >= 0) {
            val index = i + headerOffset
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.animateScrollToItem(index)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onAbout) {
                        Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.about))
                    }
                },
            )
        },
    ) { padding ->
        // imePadding: edge-to-edge disables adjustResize's effect, so lift the list above the keyboard explicitly
        Column(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize().imePadding()) {
            SearchField(query, onQuery, onEnter, searchFocus, onSearchFocus)
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (blank) {
                    if (recents.isEmpty()) {
                        item(key = "hint") {
                            Text(
                                stringResource(R.string.hint_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    } else {
                        item(key = "header") { RecentHeader(onClearRecents) }
                        items(recents, key = { it.k }) { e ->
                            KanjiRow(e, highlight && e.k == selected, inRecents = true, onPick, onCopy, onRemove)
                        }
                    }
                } else if (found.kanji.isNotEmpty()) {
                    items(found.kanji, key = { it.k }) { e ->
                        KanjiRow(e, highlight && e.k == selected, inRecents = false, onPick, onCopy, onRemove)
                    }
                } else if (ready && found.query == query && query.isNotBlank()) {
                    item(key = "none") { NoMatches() }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    onEnter: () -> Unit,
    focus: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
) {
    // A plain TextField styled as a search bar: SearchBarDefaults.InputField has no keyboardOptions,
    // and the spec needs autocorrect and capitalisation off with a Search IME action.
    // keep the caret at the end when the query changes from outside (restore, type-anywhere, Clear)
    var tfv by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    // resync only when the VM query actually changed: it lags the IME value, and overwriting that with a stale
    // query would drop the composition (Japanese input)
    var last by remember { mutableStateOf(query) }
    if (query != last) {
        last = query
        if (tfv.text != query) tfv = TextFieldValue(query, TextRange(query.length))
    }
    TextField(
        value = tfv,
        onValueChange = { tfv = it; onQuery(it.text) },
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_search))
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(onSearch = { onEnter() }),
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        textStyle = LocalTextStyle.current.ja(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .focusRequester(focus)
            .onFocusChanged { onFocusChange(it.isFocused) }
            .testTag("search"),
    )
}

@Composable
private fun RecentHeader(onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp).testTag("recent-header"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.recent), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        TextButton(onClick = onClear) { Text(stringResource(R.string.clear)) }
    }
}

@Composable
private fun NoMatches() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.no_matches))
        Text(
            stringResource(R.string.handwriting_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KanjiRow(
    e: Kanji,
    highlighted: Boolean,
    inRecents: Boolean,
    onPick: (String) -> Unit,
    onCopy: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Box {
        ListItem(
            modifier = Modifier
                .testTag("row:${e.k}")
                .combinedClickable(
                    onClick = { onPick(e.k) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menu = true
                    },
                )
                // one TalkBack item per row
                .semantics(mergeDescendants = true) {},
            leadingContent = { Text(e.k, fontSize = 40.sp, style = LocalTextStyle.current.ja().copy(lineHeight = 48.sp)) },
            headlineContent = { Text(e.m.joinToString(", "), maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Text(
                    (e.on + e.kun).joinToString("、"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.ja(),
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            ),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.copy)) }, onClick = { menu = false; onCopy(e.k) })
            if (inRecents) {
                DropdownMenuItem(text = { Text(stringResource(R.string.remove_history)) }, onClick = { menu = false; onRemove(e.k) })
            }
        }
    }
}
