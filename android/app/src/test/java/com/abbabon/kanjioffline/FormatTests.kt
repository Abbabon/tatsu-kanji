package com.abbabon.kanjioffline

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTests {
    private val labels = Labels(
        strokes = "%d strokes",
        grade = "grade %d",
        joyo = "jōyō (secondary)",
        jinmei = "jinmeiyō",
        jlpt = "JLPT %d",
        freq = "#%d freq",
    )

    @Test
    fun readingWithOkurigana() {
        assertEquals(ReadingParts("", "た", "べる", ""), readingParts("た.べる"))
    }

    @Test
    fun readingWithAffixDashes() {
        assertEquals(ReadingParts("-", "び", "", ""), readingParts("-び"))
        assertEquals(ReadingParts("", "お", "", "-"), readingParts("お-"))
        assertEquals(ReadingParts("", "スイ", "", ""), readingParts("スイ"))
    }

    @Test
    fun gradeLabels() {
        assertEquals("grade 1", gradeLabel(1, labels))
        assertEquals("jōyō (secondary)", gradeLabel(8, labels))
        assertEquals("jinmeiyō", gradeLabel(9, labels))
    }

    // type-anywhere forwards printable keys (incl. space, punctuation, kana) but not control keys
    @Test
    fun typableKeys() {
        assertTrue(isTypable("a"))
        assertTrue(isTypable(" "))
        assertTrue(isTypable("-"))
        assertTrue(isTypable("み"))
        assertFalse(isTypable(""))
        assertFalse(isTypable("\r"))
        assertFalse(isTypable("\u007F"))
        assertFalse(isTypable("\u0000"))     // what a non-character key reports
        assertFalse(isTypable("\uF700"))     // private-use key codes (an escape, never a literal)
    }

    @Test
    fun metaLineSkipsMissingValues() {
        val water = Kanji("水", listOf("water"), listOf("スイ"), listOf("みず"), emptyList(), 4, 1, 4, 223)
        assertEquals("4 strokes · grade 1 · JLPT 4 · #223 freq", metaLine(water, labels))
        val rare = Kanji("鬱", listOf("gloom"), emptyList(), emptyList(), emptyList(), 29, 0, 0, 0)
        assertEquals("29 strokes", metaLine(rare, labels))
    }

    // Review Focus 3: ASCII digits whatever the device language
    @Test
    fun metaLineUsesAsciiDigitsInEveryLocale() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            val water = Kanji("水", listOf("water"), listOf("スイ"), listOf("みず"), emptyList(), 4, 1, 4, 223)
            assertEquals("4 strokes · grade 1 · JLPT 4 · #223 freq", metaLine(water, labels))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun steppingSelection() {
        val ids = listOf("a", "b", "c")
        assertEquals("a", stepSelection(null, ids, 1))
        assertEquals("a", stepSelection(null, ids, -1))
        assertEquals("b", stepSelection("a", ids, 1))
        assertEquals("c", stepSelection("c", ids, 1))   // clamps
        assertEquals("a", stepSelection("a", ids, -1))
        assertEquals("a", stepSelection("gone", ids, 1))
        assertNull(stepSelection("a", emptyList(), 1))
    }

    // frozen Recent order: new on top, vanished dropped, the rest keep their place
    @Test
    fun mergeOrderKeepsShownOrder() {
        assertEquals(listOf("c", "a"), mergeOrder(shown = listOf("a", "b"), latest = listOf("c", "a")))
        assertEquals(listOf("a", "b"), mergeOrder(shown = listOf("a", "b"), latest = listOf("b", "a")))
        assertEquals(listOf("x"), mergeOrder(shown = emptyList(), latest = listOf("x")))
        assertEquals(emptyList<String>(), mergeOrder(shown = listOf("a"), latest = emptyList()))
    }
}