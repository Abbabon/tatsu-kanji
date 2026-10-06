package com.abbabon.kanjioffline

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.recentsStore: DataStore<Preferences> by preferencesDataStore(name = "recents")

/**
 * Kanji the user opened or copied. Local to the device. Stored as one string, newest first,
 * separated by newlines (a kanji is never a newline).
 */
class Recents(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("recent")

    private fun decode(s: String?): List<String> = if (s.isNullOrEmpty()) emptyList() else s.split('\n')

    val flow: Flow<List<String>> = store.data.map { decode(it[key]) }

    /** Insert or bump `kanji` to the top, then drop everything past the newest `keep`. */
    suspend fun touch(kanji: String, keep: Int = 100) {
        store.edit { prefs ->
            val next = (listOf(kanji) + decode(prefs[key]).filter { it != kanji }).take(keep)
            prefs[key] = next.joinToString("\n")
        }
    }

    suspend fun remove(kanji: String) {
        store.edit { prefs -> prefs[key] = decode(prefs[key]).filter { it != kanji }.joinToString("\n") }
    }

    suspend fun clear() {
        store.edit { it.remove(key) }
    }
}
