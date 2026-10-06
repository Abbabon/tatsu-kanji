package com.abbabon.kanjioffline

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val vm: TatsuViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // only on a fresh start: after a rotation the intent is delivered again and would overwrite what the user typed since
        if (savedInstanceState == null) applyProcessText(intent)
        setContent { TatsuTheme { TatsuScreen(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyProcessText(intent)
    }

    private fun applyProcessText(intent: Intent?) {
        if (intent?.action != Intent.ACTION_PROCESS_TEXT) return
        intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.let { vm.applyHandOff(capHandOff(it)) }
    }

    // the query lives in SavedStateHandle (Binder-limited): bound what other apps can push in, without splitting a surrogate pair
    private fun capHandOff(t: String): String {
        if (t.length <= 1000) return t
        return t.take(if (t[999].isHighSurrogate()) 999 else 1000)
    }
}
