package com.abbabon.kanjioffline

import kotlin.math.abs

/** Katakana (ァ-ヶ) to hiragana; everything else unchanged. */
fun toHira(s: String): String = buildString(s.length) {
    for (c in s) append(if (c.code in 0x30A1..0x30F6) (c.code - 0x60).toChar() else c)
}

// kana -> wapuro romaji (what people actually type). Digraphs are looked up first so they win.
private val roma: Map<String, String> = run {
    val table = "きゃkya きゅkyu きょkyo しゃsha しゅshu しょsho ちゃcha ちゅchu ちょcho にゃnya にゅnyu にょnyo " +
        "ひゃhya ひゅhyu ひょhyo みゃmya みゅmyu みょmyo りゃrya りゅryu りょryo ぎゃgya ぎゅgyu ぎょgyo " +
        "じゃja じゅju じょjo ぢゃja ぢゅju ぢょjo びゃbya びゅbyu びょbyo ぴゃpya ぴゅpyu ぴょpyo " +
        "あa いi うu えe おo かka きki くku けke こko さsa しshi すsu せse そso たta ちchi つtsu てte とto " +
        "なna にni ぬnu ねne のno はha ひhi ふfu へhe ほho まma みmi むmu めme もmo やya ゆyu よyo " +
        "らra りri るru れre ろro わwa ゐwi ゑwe をwo んn がga ぎgi ぐgu げge ごgo ざza じji ずzu ぜze ぞzo " +
        "だda ぢji づzu でde どdo ばba びbi ぶbu べbe ぼbo ぱpa ぴpi ぷpu ぺpe ぽpo ぁa ぃi ぅu ぇe ぉo ゔvu"
    table.split(' ').filter { it.isNotEmpty() }.associate { token ->
        val kana = token.takeWhile { it !in 'a'..'z' }
        kana to token.drop(kana.length)
    }
}

fun romaji(kana: String): String {
    val k = toHira(kana).filter { it != '.' && it != '-' && it != 'ー' }
    val out = StringBuilder()
    var i = 0
    while (i < k.length) {
        if (k[i] == 'っ') {
            // small tsu doubles the next consonant ("tch" for ch)
            val rest = romaji(k.substring(i + 1))
            val double = rest.firstOrNull()?.let {
                if (it in "aeiou") "" else if (it == 'c') "t" else it.toString()
            } ?: ""
            return out.toString() + double + rest
        }
        val two = if (i + 1 < k.length) roma[k.substring(i, i + 2)] else null
        if (two != null) {
            out.append(two)
            i += 2
        } else {
            out.append(roma[k[i].toString()] ?: k[i].toString())
            i += 1
        }
    }
    return out.toString()
}

/** True if a and b differ by at most one insertion, deletion, substitution or adjacent swap. */
private fun within1(a: String, b: String): Boolean {
    if (abs(a.length - b.length) > 1) return false
    var i = 0
    var j = 0
    var used = false
    while (i < a.length && j < b.length) {
        if (a[i] == b[j]) { i++; j++; continue }
        if (used) return false
        used = true
        if (a.length == b.length && i + 1 < a.length && j + 1 < b.length && a[i] == b[j + 1] && a[i + 1] == b[j]) {
            i += 2; j += 2; continue
        }
        if (a.length > b.length) i++ else if (a.length < b.length) j++ else { i++; j++ }
    }
    return true
}

/** True if all of q's characters appear in s, in order. */
private fun subseq(q: String, s: String): Boolean {
    var want = 0
    for (c in s) if (want < q.length && c == q[want]) want++
    return want == q.length
}

private fun score(q: String, s: String): Int {
    if (s == q) return 100
    if (s.startsWith(q)) return 80
    if ((" $s").contains(" $q")) return 70
    if (s.contains(q)) return 55
    if (q.length >= 4 && s.split(' ').any { it.isNotEmpty() && within1(q, it) }) return 50
    if (q.length >= 3 && subseq(q, s)) return 30
    return 0
}

/** In-memory index over all kanji. Build once at launch. */
// ponytail: linear scan of ~10k entries per keystroke (tens of ms, run off the main thread); add a prefix index if a words pack makes it slow
class Searcher(list: List<Kanji>) {
    private class Entry(
        val kanji: Kanji,
        val kana: List<String>,   // hiragana, no "." or "-"
        val text: List<String>,   // lowercased meanings + romaji
    )

    private class Hit(val score: Int, val index: Int, val kanji: Kanji)

    private val entries: List<Entry> = list.map { e ->
        val kana = (e.on + e.kun).map { r -> toHira(r).filter { it != '.' && it != '-' } }
        Entry(e, kana, e.m.map { it.lowercase() } + kana.map(::romaji))
    }
    private val byKanji: Map<String, Kanji> = HashMap<String, Kanji>().also { map ->
        for (e in list) map.putIfAbsent(e.k, e)
    }

    fun kanji(character: String): Kanji? = byKanji[character]

    fun search(query: String, limit: Int = 60): List<Kanji> {
        val q = toHira(query.trim().lowercase())
        if (q.isEmpty()) return emptyList()

        // any known kanji in the input: list those, in order, once each (by code point, so non-BMP kanji stay whole)
        val seen = HashSet<String>()
        val listed = ArrayList<Kanji>()
        var i = 0
        while (i < q.length) {
            val cp = q.codePointAt(i)
            val s = String(Character.toChars(cp))
            i += s.length
            val e = byKanji[s]
            if (e != null && seen.add(s)) listed.add(e)
        }
        if (listed.isNotEmpty()) return listed

        val isKana = q.all { it.code in 0x3041..0x309F }
        val hits = ArrayList<Hit>()
        for ((index, e) in entries.withIndex()) {
            var best = 0
            for (field in if (isKana) e.kana else e.text) {
                best = maxOf(best, score(q, field))
                if (best == 100) break
            }
            if (best > 0) hits.add(Hit(best, index, e.kanji))
        }
        // higher score, then more frequent (unranked last), then data order (stable)
        fun rank(f: Int) = if (f == 0) 9999 else f
        return hits
            .sortedWith(compareByDescending<Hit> { it.score }.thenBy { rank(it.kanji.f) }.thenBy { it.index })
            .take(limit)
            .map { it.kanji }
    }
}
