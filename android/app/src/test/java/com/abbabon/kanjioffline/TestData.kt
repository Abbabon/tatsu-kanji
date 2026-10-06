package com.abbabon.kanjioffline

import java.io.File

/** The real data file, shared by all JVM tests. */
object TestData {
    val all: List<Kanji> by lazy {
        parseKanji(File(System.getProperty("kanji.json")!!).readText())
    }
}
