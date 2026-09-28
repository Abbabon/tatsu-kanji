import Foundation

/// Katakana (ァ-ヶ) to hiragana; everything else unchanged.
func toHira(_ s: String) -> String {
    String(String.UnicodeScalarView(s.unicodeScalars.map { c in
        (0x30A1...0x30F6).contains(c.value) ? Unicode.Scalar(c.value - 0x60)! : c
    }))
}

// kana -> wapuro romaji (what people actually type). Digraphs are looked up first so they win.
private let roma: [String: String] = {
    let table = "きゃkya きゅkyu きょkyo しゃsha しゅshu しょsho ちゃcha ちゅchu ちょcho にゃnya にゅnyu にょnyo " +
        "ひゃhya ひゅhyu ひょhyo みゃmya みゅmyu みょmyo りゃrya りゅryu りょryo ぎゃgya ぎゅgyu ぎょgyo " +
        "じゃja じゅju じょjo ぢゃja ぢゅju ぢょjo びゃbya びゅbyu びょbyo ぴゃpya ぴゅpyu ぴょpyo " +
        "あa いi うu えe おo かka きki くku けke こko さsa しshi すsu せse そso たta ちchi つtsu てte とto " +
        "なna にni ぬnu ねne のno はha ひhi ふfu へhe ほho まma みmi むmu めme もmo やya ゆyu よyo " +
        "らra りri るru れre ろro わwa ゐwi ゑwe をwo んn がga ぎgi ぐgu げge ごgo ざza じji ずzu ぜze ぞzo " +
        "だda ぢji づzu でde どdo ばba びbi ぶbu べbe ぼbo ぱpa ぴpi ぷpu ぺpe ぽpo ぁa ぃi ぅu ぇe ぉo ゔvu"
    var out: [String: String] = [:]
    for token in table.split(separator: " ") {
        let kana = token.prefix { !("a"..."z").contains($0) }
        out[String(kana)] = String(token.dropFirst(kana.count))
    }
    return out
}()

func romaji(_ kana: String) -> String {
    let k = Array(toHira(kana).filter { $0 != "." && $0 != "-" && $0 != "ー" })
    var out = ""
    var i = 0
    while i < k.count {
        if k[i] == "っ" {
            // small tsu doubles the next consonant ("tch" for ch)
            let rest = romaji(String(k[(i + 1)...]))
            let double = rest.first.map { "aeiou".contains($0) ? "" : $0 == "c" ? "t" : String($0) } ?? ""
            return out + double + rest
        }
        if i + 1 < k.count, let two = roma[String(k[i...i + 1])] {
            out += two
            i += 2
        } else {
            out += roma[String(k[i])] ?? String(k[i])
            i += 1
        }
    }
    return out
}

/// Edit distance <= 1 (insert, delete, substitute, or swap two neighbours).
private func within1(_ a: [Character], _ b: [Character]) -> Bool {
    if abs(a.count - b.count) > 1 { return false }
    var i = 0, j = 0, used = false
    while i < a.count && j < b.count {
        if a[i] == b[j] { i += 1; j += 1; continue }
        if used { return false }
        used = true
        if a.count == b.count, i + 1 < a.count, j + 1 < b.count, a[i] == b[j + 1], a[i + 1] == b[j] {
            i += 2; j += 2; continue
        }
        if a.count > b.count { i += 1 } else if a.count < b.count { j += 1 } else { i += 1; j += 1 }
    }
    return true
}

/// True if all of q's characters appear in s, in order.
private func subseq(_ q: String, _ s: String) -> Bool {
    var it = q.makeIterator()
    var want = it.next()
    for c in s where c == want { want = it.next() }
    return want == nil
}

private func score(_ q: String, _ s: String) -> Int {
    if s == q { return 100 }
    if s.hasPrefix(q) { return 80 }
    if (" " + s).contains(" " + q) { return 70 }
    if s.contains(q) { return 55 }
    if q.count >= 4 {
        let qc = Array(q)
        if s.split(separator: " ").contains(where: { within1(qc, Array($0)) }) { return 50 }
    }
    if q.count >= 3 && subseq(q, s) { return 30 }
    return 0
}

/// In-memory index over all kanji. Build once at launch.
// ponytail: linear scan of ~10k entries per keystroke (a few ms); add a prefix index if the words pack makes it slow
struct Searcher: Sendable {
    private struct Entry: Sendable {
        let kanji: Kanji
        let meanings: [String]  // lowercased
        let kana: [String]      // hiragana, no "." or "-"
        let roma: [String]
    }

    private let entries: [Entry]
    private let byKanji: [String: Kanji]

    init(_ list: [Kanji]) {
        entries = list.map { e in
            let kana = (e.on + e.kun).map { toHira($0).filter { $0 != "." && $0 != "-" } }
            return Entry(kanji: e, meanings: e.m.map { $0.lowercased() }, kana: kana, roma: kana.map(romaji))
        }
        byKanji = Dictionary(list.map { ($0.k, $0) }, uniquingKeysWith: { first, _ in first })
    }

    func kanji(_ character: String) -> Kanji? { byKanji[character] }

    func search(_ query: String, limit: Int = 60) -> [Kanji] {
        let q = toHira(query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased())
        if q.isEmpty { return [] }

        // any known kanji in the input: list those, in order, once each
        var seen = Set<String>()
        var listed: [Kanji] = []
        for c in q {
            let s = String(c)
            if let e = byKanji[s], seen.insert(s).inserted { listed.append(e) }
        }
        if !listed.isEmpty { return listed }

        let isKana = q.unicodeScalars.allSatisfy { (0x3041...0x309F).contains($0.value) }
        var hits: [(score: Int, index: Int, kanji: Kanji)] = []
        for (index, e) in entries.enumerated() {
            var best = 0
            for field in isKana ? e.kana : e.meanings + e.roma {
                best = max(best, score(q, field))
                if best == 100 { break }
            }
            if best > 0 { hits.append((best, index, e.kanji)) }
        }
        // higher score, then more frequent (unranked last), then data order (JS sort is stable)
        func rank(_ f: Int) -> Int { f == 0 ? 9999 : f }
        hits.sort { a, b in
            if a.score != b.score { return a.score > b.score }
            if rank(a.kanji.f) != rank(b.kanji.f) { return rank(a.kanji.f) < rank(b.kanji.f) }
            return a.index < b.index
        }
        return hits.prefix(limit).map(\.kanji)
    }
}
