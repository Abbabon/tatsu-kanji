import Testing
@testable import Tatsu

struct DataTests {
    @Test func bundledDataLoads() throws {
        let all = try Kanji.loadBundled()
        // 10,384 in the current KANJIDIC2 build; the exact count changes when build_data.py is re-run
        #expect(all.count > 10_000)
        #expect(all[0].k == "日")
        let water = try #require(all.first { $0.k == "水" })
        #expect(water.m.contains("water"))
        #expect(water.kun.contains("みず"))
        #expect(water.s == 4)
    }

    // Swift compares Strings by canonical equivalence, so CJK compatibility ideographs
    // (e.g. U+FA19 vs 神) would collide as list IDs
    @Test func kanjiIDsAreUnique() throws {
        let all = try Kanji.loadBundled()
        #expect(Set(all.map(\.k)).count == all.count)
    }
}

struct RomajiTests {
    // same cases as the old test.js
    @Test func romajiConversion() {
        #expect(romaji("きょう") == "kyou")
        #expect(romaji("ニチ") == "nichi")
        #expect(romaji("た.べる") == "taberu")
        #expect(romaji("まっちゃ") == "matcha")
        #expect(romaji("がっこう") == "gakkou")
        #expect(romaji("じゅう") == "juu")
    }

    @Test func sokuonAtEndDoesNotCrash() {
        #expect(romaji("あっ") == "a")
    }

    @Test func katakanaToHiragana() {
        #expect(toHira("ミズ") == "みず")
        #expect(toHira("water") == "water")
    }
}

struct SearchTests {
    let searcher: Searcher
    init() throws { searcher = Searcher(try Kanji.loadBundled()) }

    func first(_ q: String) -> String? { searcher.search(q).first?.k }
    func top3(_ q: String) -> [String] { searcher.search(q).prefix(3).map(\.k) }

    // same cases as the old test.js
    @Test func rankingMatchesWebVersion() {
        #expect(first("water") == "水")
        #expect(first("みず") == "水")
        #expect(first("ミズ") == "水")
        #expect(first("mizu") == "水")
        #expect(first("sun") == "日")
        #expect(first("nichi") == "日")
        #expect(first("taberu") == "食")
    }

    @Test func kanjiTextListsEachKanji() {
        #expect(searcher.search("日本語").map(\.k) == ["日", "本", "語"])
    }

    @Test func typoTolerance() {
        #expect(top3("watr").contains("水"))
        #expect(top3("mountian").contains("山"))
    }

    @Test func blankQueryIsEmpty() {
        #expect(searcher.search("   ").isEmpty)
        #expect(searcher.search("").isEmpty)
        #expect(searcher.search("\u{3000}").isEmpty)
    }

    // Review Focus
    @Test func uppercaseMatchesLowercase() {
        #expect(first("WATER") == "水")
    }

    @Test func repeatedKanjiListedOnce() {
        #expect(searcher.search("日日本").map(\.k) == ["日", "本"])
    }

    @Test func kanjiMixedWithLatinListsOnlyKanji() {
        #expect(searcher.search("日本 water").map(\.k) == ["日", "本"])
    }

    @Test func unknownCharactersReturnNothing() {
        #expect(searcher.search("🍣").isEmpty)
        #expect(searcher.search("〇〇").isEmpty)
    }

    @Test func lookupByCharacter() {
        #expect(searcher.kanji("水")?.m.contains("water") == true)
        #expect(searcher.kanji("x") == nil)
    }

    @Test func limitIsRespected() {
        #expect(searcher.search("a", limit: 5).count <= 5)
    }
}
