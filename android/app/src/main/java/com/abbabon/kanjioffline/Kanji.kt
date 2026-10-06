package com.abbabon.kanjioffline

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One KANJIDIC2 entry. Short keys match kanji.json (see build_data.py). */
@Serializable
data class Kanji(
    val k: String,            // the character
    val m: List<String>,      // English meanings
    val on: List<String>,     // on'yomi, katakana
    val kun: List<String>,    // kun'yomi, hiragana; "." marks okurigana, "-" marks a prefix/suffix
    val n: List<String>,      // nanori (name readings)
    val s: Int,               // stroke count
    val g: Int,               // school grade, 0 = none
    val j: Int,               // JLPT level (old 1-4 scale), 0 = none
    val f: Int,               // frequency rank, 0 = unranked
)

private val json = Json { ignoreUnknownKeys = true }

fun parseKanji(text: String): List<Kanji> = json.decodeFromString(text)

/** Reads the bundled asset. A failure here is a build bug, so let it crash (like fatalError on iOS). */
fun Context.loadKanji(): List<Kanji> =
    assets.open("kanji.json").use { parseKanji(it.readBytes().decodeToString()) }
