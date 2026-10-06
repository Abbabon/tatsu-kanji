package com.abbabon.kanjioffline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataTests {
    @Test
    fun bundledDataLoads() {
        val all = TestData.all
        // 10,348 in the current KANJIDIC2 build; the exact count changes when build_data.py is re-run
        assertTrue(all.size > 10_000)
        assertEquals("日", all[0].k)
        val water = all.first { it.k == "水" }
        assertTrue("water" in water.m)
        assertTrue("みず" in water.kun)
        assertEquals(4, water.s)
    }

    @Test
    fun kanjiIDsAreUnique() {
        val all = TestData.all
        assertEquals(all.size, all.map { it.k }.toSet().size)
    }

    // 8 entries are outside the BMP (one UTF-16 surrogate pair each); every k is exactly one code point
    @Test
    fun everyKanjiIsOneCodePoint() {
        assertTrue(TestData.all.all { it.k.codePointCount(0, it.k.length) == 1 })
        assertNotNull(TestData.all.firstOrNull { it.k == "𠮟" })
    }
}
