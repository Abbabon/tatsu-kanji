package com.abbabon.kanjioffline

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecentsTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var scope: CoroutineScope
    private lateinit var recents: Recents

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        recents = Recents(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "recents.preferences_pb") })
    }

    @After
    fun tearDown() = scope.cancel()

    private fun all(): List<String> = runBlocking { recents.flow.first() }

    @Test
    fun startsEmpty() {
        assertTrue(all().isEmpty())
    }

    @Test
    fun newestFirst() = runBlocking {
        recents.touch("水")
        recents.touch("日")
        assertEquals(listOf("日", "水"), all())
    }

    @Test
    fun touchingAgainMovesToTopWithoutDuplicate() = runBlocking {
        recents.touch("水")
        recents.touch("日")
        recents.touch("水")
        assertEquals(listOf("水", "日"), all())
    }

    @Test
    fun keepsOnlyNewestHundred() = runBlocking {
        // 101 distinct CJK characters starting at 一 (U+4E00)
        val chars = (0 until 101).map { (0x4E00 + it).toChar().toString() }
        for (c in chars) recents.touch(c)
        val kept = all()
        assertEquals(100, kept.size)
        assertEquals(chars[100], kept.first())
        assertFalse(chars[0] in kept)
    }

    @Test
    fun clearRemovesEverything() = runBlocking {
        recents.touch("水")
        recents.touch("日")
        recents.clear()
        assertTrue(all().isEmpty())
    }

    @Test
    fun removeDropsOnlyThatKanji() = runBlocking {
        recents.touch("水")
        recents.touch("日")
        recents.remove("日")
        assertEquals(listOf("水"), all())
    }

    @Test
    fun removingMissingKanjiIsNoOp() = runBlocking {
        recents.touch("水")
        recents.remove("火")
        assertEquals(listOf("水"), all())
    }

    // Review Focus 4: a kanji outside the BMP survives the one-string encoding intact
    @Test
    fun nonBmpKanjiRoundTrips() = runBlocking {
        recents.touch("水")
        recents.touch("𠮟")
        assertEquals(listOf("𠮟", "水"), all())
        recents.remove("𠮟")
        assertEquals(listOf("水"), all())
    }
}
