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

    @Test func steppingSelection() {
        let ids = ["a", "b", "c"]
        #expect(stepSelection(nil, in: ids, by: 1) == "a")
        #expect(stepSelection(nil, in: ids, by: -1) == "a")
        #expect(stepSelection("a", in: ids, by: 1) == "b")
        #expect(stepSelection("c", in: ids, by: 1) == "c")  // clamps
        #expect(stepSelection("a", in: ids, by: -1) == "a")
        #expect(stepSelection("gone", in: ids, by: 1) == "a")
        #expect(stepSelection("a", in: [], by: 1) == nil)
    }

    // Mac key monitor: type-anywhere into search, arrows step results only from the search field
    @Test func macKeyRouting() {
        func route(_ code: UInt16, _ chars: String, shortcut: Bool = false, shift: Bool = false,
                   _ focus: KeyFocus = .other, queryEmpty: Bool = false) -> KeyRoute {
            keyRoute(keyCode: code, characters: chars, shortcut: shortcut, shift: shift, focus: focus, queryEmpty: queryEmpty)
        }
        #expect(route(13, "w") == .toSearch)                       // nothing / list / detail focused
        #expect(route(13, "W", shift: true) == .toSearch)
        #expect(route(49, " ") == .toSearch)
        #expect(route(13, "w", .searchField) == .pass)             // field already has it
        #expect(route(13, "w", .textInput) == .pass)
        #expect(route(3, "f", shortcut: true) == .pass)            // ⌘F etc.
        #expect(route(51, "\u{7F}") == .toSearch)                 // backspace edits the query
        #expect(route(51, "\u{7F}", queryEmpty: true) == .pass)
        #expect(route(36, "\r") == .pass)                         // return
        #expect(route(53, "\u{1B}") == .pass)                     // escape
        #expect(route(48, "\t") == .pass)                         // tab
        #expect(route(122, "\u{F704}") == .pass)                  // F1
        #expect(route(125, "\u{F701}", .searchField) == .step(1))
        #expect(route(126, "\u{F700}", .searchField) == .step(-1))
        #expect(route(125, "\u{F701}", shift: true, .searchField) == .pass)
        #expect(route(125, "\u{F701}") == .pass)                  // list handles its own arrows
    }
}
