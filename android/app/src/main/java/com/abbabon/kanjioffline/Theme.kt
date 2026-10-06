package com.abbabon.kanjioffline

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList

// Below Android 12 there is no dynamic colour: a static scheme seeded from the logo's red (#C0392B).
private val StaticLight = lightColorScheme(
    primary = Color(0xFFC0392B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD4),
    onPrimaryContainer = Color(0xFF410002),
    secondaryContainer = Color(0xFFFFDAD4),
    onSecondaryContainer = Color(0xFF410002),
)
private val StaticDark = darkColorScheme(
    primary = Color(0xFFFFB4A8),
    onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF93000A),
    onPrimaryContainer = Color(0xFFFFDAD4),
    secondaryContainer = Color(0xFF5C3F3B),
    onSecondaryContainer = Color(0xFFFFDAD4),
)

@Composable
fun TatsuTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> StaticDark
        else -> StaticLight
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private val japanese = LocaleList("ja")

/** Han characters use Japanese glyphs, not Chinese ones. Apply to every text that shows kanji or kana. */
fun TextStyle.ja(): TextStyle = copy(localeList = japanese)
