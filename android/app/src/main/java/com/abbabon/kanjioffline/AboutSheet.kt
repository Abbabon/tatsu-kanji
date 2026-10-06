package com.abbabon.kanjioffline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.titleMedium.ja())
            Text(stringResource(R.string.about_blurb), color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text(stringResource(R.string.about_data_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.about_data_credit))
            LinkText(stringResource(R.string.link_kanjidic), "https://www.edrdg.org/wiki/index.php/KANJIDIC_Project")
            LinkText(stringResource(R.string.link_cc), "https://creativecommons.org/licenses/by-sa/4.0/")

            Text(stringResource(R.string.about_app_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.about_mit))
            Text(stringResource(R.string.about_font_credit), style = LocalTextStyle.current.ja(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinkText(stringResource(R.string.link_source), "https://github.com/Abbabon/tatsu-kanji")
        }
    }
}

@Composable
private fun LinkText(label: String, url: String) {
    val style = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    Text(buildAnnotatedString { withLink(LinkAnnotation.Url(url, style)) { append(label) } })
}
