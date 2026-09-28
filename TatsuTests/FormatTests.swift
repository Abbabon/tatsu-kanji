import Testing
@testable import Tatsu

struct FormatTests {
    @Test func readingWithOkurigana() {
        #expect(readingParts("た.べる") == ReadingParts(prefix: "", kana: "た", okurigana: "べる", suffix: ""))
    }

    @Test func readingWithAffixDashes() {
        #expect(readingParts("-び") == ReadingParts(prefix: "-", kana: "び", okurigana: "", suffix: ""))
        #expect(readingParts("お-") == ReadingParts(prefix: "", kana: "お", okurigana: "", suffix: "-"))
        #expect(readingParts("スイ") == ReadingParts(prefix: "", kana: "スイ", okurigana: "", suffix: ""))
    }

    @Test func gradeLabels() {
        #expect(gradeLabel(1) == "grade 1")
        #expect(gradeLabel(8) == "jōyō (secondary)")
        #expect(gradeLabel(9) == "jinmeiyō")
    }

    // type-anywhere forwards printable keys (incl. space, punctuation, kana) but not arrows or control keys
    @Test func typableKeys() {
        #expect(isTypable("a"))
        #expect(isTypable(" "))
        #expect(isTypable("-"))
        #expect(isTypable("み"))
        #expect(!isTypable(""))
        #expect(!isTypable("\r"))
        #expect(!isTypable("\u{7F}"))
        #expect(!isTypable("\u{F700}"))  // up arrow on macOS
    }

    @Test func metaLineSkipsMissingValues() {
        let water = Kanji(k: "水", m: ["water"], on: ["スイ"], kun: ["みず"], n: [], s: 4, g: 1, j: 4, f: 223)
        #expect(metaLine(water) == "4 strokes · grade 1 · JLPT 4 · #223 freq")
        let rare = Kanji(k: "鬱", m: ["gloom"], on: [], kun: [], n: [], s: 29, g: 0, j: 0, f: 0)
        #expect(metaLine(rare) == "29 strokes")
    }
}
