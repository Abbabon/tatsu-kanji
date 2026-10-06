package com.abbabon.kanjioffline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomajiTests {
    @Test
    fun romajiConversion() {
        assertEquals("kyou", romaji("きょう"))
        assertEquals("nichi", romaji("ニチ"))
        assertEquals("taberu", romaji("た.べる"))
        assertEquals("matcha", romaji("まっちゃ"))
        assertEquals("gakkou", romaji("がっこう"))
        assertEquals("juu", romaji("じゅう"))
    }

    @Test
    fun sokuonAtEndDoesNotCrash() {
        assertEquals("a", romaji("あっ"))
    }

    @Test
    fun katakanaToHiragana() {
        assertEquals("みず", toHira("ミズ"))
        assertEquals("water", toHira("water"))
    }
}

class SearchTests {
    private val searcher = TestData.searcher

    private fun first(q: String) = searcher.search(q).firstOrNull()?.k
    private fun top3(q: String) = searcher.search(q).take(3).map { it.k }
    private fun keys(q: String) = searcher.search(q).map { it.k }

    @Test
    fun rankingMatchesWebVersion() {
        assertEquals("水", first("water"))
        assertEquals("水", first("みず"))
        assertEquals("水", first("ミズ"))
        assertEquals("水", first("mizu"))
        assertEquals("日", first("sun"))
        assertEquals("日", first("nichi"))
        assertEquals("食", first("taberu"))
    }

    @Test
    fun kanjiTextListsEachKanji() {
        assertEquals(listOf("日", "本", "語"), keys("日本語"))
    }

    @Test
    fun typoTolerance() {
        assertTrue("水" in top3("watr"))
        assertTrue("山" in top3("mountian"))
    }

    @Test
    fun blankQueryIsEmpty() {
        assertTrue(searcher.search("   ").isEmpty())
        assertTrue(searcher.search("").isEmpty())
        assertTrue(searcher.search("　").isEmpty())
    }

    @Test
    fun uppercaseMatchesLowercase() {
        assertEquals("水", first("WATER"))
    }

    @Test
    fun repeatedKanjiListedOnce() {
        assertEquals(listOf("日", "本"), keys("日日本"))
    }

    @Test
    fun kanjiMixedWithLatinListsOnlyKanji() {
        assertEquals(listOf("日", "本"), keys("日本 water"))
    }

    @Test
    fun unknownCharactersReturnNothing() {
        assertTrue(searcher.search("🍣").isEmpty())
        assertTrue(searcher.search("〇〇").isEmpty())
    }

    @Test
    fun lookupByCharacter() {
        assertTrue(searcher.kanji("水")?.m?.contains("water") == true)
        assertNull(searcher.kanji("x"))
    }

    @Test
    fun limitIsRespected() {
        assertTrue(searcher.search("a", limit = 5).size <= 5)
        assertTrue(searcher.search("a").size <= 60)
    }

    // Review Focus 1: a kanji outside the BMP is one code point, listed whole and once
    @Test
    fun nonBmpKanjiIsListedWhole() {
        assertNotNull(searcher.kanji("𠮟"))
        assertEquals(listOf("𠮟", "日", "本"), keys("𠮟日𠮟本"))
    }

    // Review Focus 2: a huge pasted query neither crashes nor hangs (generous 5 s bound)
    @Test
    fun veryLongQueryReturns() {
        val start = System.nanoTime()
        searcher.search("a".repeat(5_000))
        val r = searcher.search("mizu".repeat(1_250))
        assertTrue(r.size <= 60)
        assertTrue(searcher.search("a".repeat(5_000)).size <= 60)
        assertTrue((System.nanoTime() - start) / 1_000_000 < 5_000)
    }
}
