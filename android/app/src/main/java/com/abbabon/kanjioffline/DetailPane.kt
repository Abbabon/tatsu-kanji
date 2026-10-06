package com.abbabon.kanjioffline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailPane(kanji: Kanji, labels: Labels, showBack: Boolean, onBack: () -> Unit, onCopy: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = { TextButton(onClick = onCopy) { Text(stringResource(R.string.copy)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState(), enabled = true)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SelectionContainer {
                Text(
                    kanji.k,
                    fontSize = 96.sp,
                    style = LocalTextStyle.current.ja().copy(lineHeight = 112.sp),
                    modifier = Modifier.testTag("detail-kanji"),
                )
            }
            Text(kanji.m.joinToString(", "), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            ReadingSection(stringResource(R.string.on_label), kanji.k, kanji.on)
            ReadingSection(stringResource(R.string.kun_label), kanji.k, kanji.kun)
            if (kanji.n.isNotEmpty()) {
                Section(stringResource(R.string.names_label)) {
                    Text(
                        kanji.n.joinToString("、"),
                        style = MaterialTheme.typography.bodyLarge.ja(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                metaLine(kanji, labels),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

/** Furigana stand-in: each reading is kana / kanji+okurigana / romaji stacked in a tonal tile. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadingSection(label: String, kanji: String, readings: List<String>) {
    if (readings.isEmpty()) return
    Section(label) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in readings) {
                val p = readingParts(r)
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                    // one TalkBack item per tile
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).semantics(mergeDescendants = true) {}) {
                        Text(p.kana, style = MaterialTheme.typography.labelMedium.ja(), color = MaterialTheme.colorScheme.primary)
                        Text(p.prefix + kanji + p.okurigana + p.suffix, style = MaterialTheme.typography.titleMedium.ja())
                        Text(romaji(r), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyDetail() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.detail_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.detail_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
