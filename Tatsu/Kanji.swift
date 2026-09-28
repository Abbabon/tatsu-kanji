import Foundation

/// One KANJIDIC2 entry. Short keys match kanji.json (see build_data.py).
struct Kanji: Codable, Hashable, Identifiable, Sendable {
    let k: String      // the character
    let m: [String]    // English meanings
    let on: [String]   // on'yomi, katakana
    let kun: [String]  // kun'yomi, hiragana; "." marks okurigana, "-" marks a prefix/suffix
    let n: [String]    // nanori (name readings)
    let s: Int         // stroke count
    let g: Int         // school grade, 0 = none
    let j: Int         // JLPT level (old 1-4 scale), 0 = none
    let f: Int         // frequency rank, 0 = unranked

    var id: String { k }

    static func loadBundled() throws -> [Kanji] {
        guard let url = Bundle.main.url(forResource: "kanji", withExtension: "json") else {
            throw CocoaError(.fileNoSuchFile)
        }
        return try JSONDecoder().decode([Kanji].self, from: Data(contentsOf: url))
    }
}
