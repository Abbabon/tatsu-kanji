package com.abbabon.kanjioffline

import java.util.Locale

/**
 * "た.べる" -> kana た (shown over the kanji), okurigana べる after it.
 * "-び" / "お-" keep their dash outside, marking a suffix / prefix reading.
 */
data class ReadingParts(val prefix: String, val kana: String, val okurigana: String, val suffix: String)

fun readingParts(reading: String): ReadingParts {
    val pieces = reading.split('.', limit = 2)
    var stem = pieces[0]
    val okurigana = if (pieces.size > 1) pieces[1] else ""
    val prefix = if (stem.startsWith("-")) "-" else ""
    if (prefix.isNotEmpty()) stem = stem.removePrefix("-")
    val suffix = if (stem.endsWith("-")) "-" else ""
    if (suffix.isNotEmpty()) stem = stem.removeSuffix("-")
    return ReadingParts(prefix, stem, okurigana, suffix)
}

/** Label text from strings.xml, passed in so this file stays free of Android. */
class Labels(
    val strokes: String,  // "%d strokes"
    val grade: String,    // "grade %d"
    val joyo: String,     // "jōyō (secondary)"
    val jinmei: String,   // "jinmeiyō"
    val jlpt: String,     // "JLPT %d"
    val freq: String,     // "#%d freq"
)

// Locale.ROOT: always ASCII digits, whatever language the device is set to
private fun fmt(template: String, n: Int) = String.format(Locale.ROOT, template, n)

fun gradeLabel(g: Int, labels: Labels): String =
    if (g <= 6) fmt(labels.grade, g) else if (g == 8) labels.joyo else labels.jinmei

fun metaLine(e: Kanji, labels: Labels): String {
    val parts = mutableListOf(fmt(labels.strokes, e.s))
    if (e.g > 0) parts.add(gradeLabel(e.g, labels))
    if (e.j > 0) parts.add(fmt(labels.jlpt, e.j))
    if (e.f > 0) parts.add(fmt(labels.freq, e.f))
    return parts.joinToString(" · ")
}

/** A key press worth forwarding to the search field: printable text, not control keys or private-use key codes. */
fun isTypable(characters: String): Boolean =
    characters.isNotEmpty() && characters.all { !it.isISOControl() && it.code !in 0xF700..0xF8FF }

/** The id one step down (+1) or up (-1) from `current`, clamped to the ends. With nothing (or something no longer listed) selected, the first id. */
fun stepSelection(current: String?, ids: List<String>, delta: Int): String? {
    val i = current?.let { ids.indexOf(it) }?.takeIf { it >= 0 } ?: return ids.firstOrNull()
    return ids[(i + delta).coerceIn(0, ids.size - 1)]
}

/** What Enter opens: the selection if it is still listed, else the first id. */
fun enterTarget(selected: String?, ids: List<String>): String? =
    selected?.takeIf { it in ids } ?: ids.firstOrNull()

/** New recents go on top, vanished ones drop out, everything else keeps its place. */
fun mergeOrder(shown: List<String>, latest: List<String>): List<String> =
    latest.filter { it !in shown } + shown.filter { it in latest }
