import Foundation
#if canImport(UIKit)
import UIKit
#else
import AppKit
#endif

/// "た.べる" -> kana た (shown over the kanji), okurigana べる after it.
/// "-び" / "お-" keep their dash outside, marking a suffix / prefix reading.
struct ReadingParts: Equatable {
    let prefix, kana, okurigana, suffix: String
}

func readingParts(_ reading: String) -> ReadingParts {
    let pieces = reading.split(separator: ".", maxSplits: 1, omittingEmptySubsequences: false)
    var stem = String(pieces[0])
    let okurigana = pieces.count > 1 ? String(pieces[1]) : ""
    let prefix = stem.hasPrefix("-") ? "-" : ""
    if !prefix.isEmpty { stem.removeFirst() }
    let suffix = stem.hasSuffix("-") ? "-" : ""
    if !suffix.isEmpty { stem.removeLast() }
    return ReadingParts(prefix: prefix, kana: stem, okurigana: okurigana, suffix: suffix)
}

func gradeLabel(_ g: Int) -> String {
    g <= 6 ? "grade \(g)" : g == 8 ? "jōyō (secondary)" : "jinmeiyō"
}

func metaLine(_ e: Kanji) -> String {
    var parts = ["\(e.s) strokes"]
    if e.g > 0 { parts.append(gradeLabel(e.g)) }
    if e.j > 0 { parts.append("JLPT \(e.j)") }
    if e.f > 0 { parts.append("#\(e.f) freq") }
    return parts.joined(separator: " · ")
}

/// A key press worth forwarding to the search field: printable text, not control keys
/// or the private-use characters macOS uses for arrow/function keys (U+F700–U+F8FF).
func isTypable(_ characters: String) -> Bool {
    !characters.isEmpty && characters.unicodeScalars.allSatisfy { c in
        !CharacterSet.controlCharacters.contains(c) && !(0xF700...0xF8FF).contains(c.value)
    }
}

func copyToPasteboard(_ s: String) {
    #if canImport(UIKit)
    UIPasteboard.general.string = s
    #else
    NSPasteboard.general.clearContents()
    NSPasteboard.general.setString(s, forType: .string)
    #endif
}
