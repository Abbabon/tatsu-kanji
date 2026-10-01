import SwiftUI

struct KanjiDetail: View {
    let kanji: Kanji
    let onCopy: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack(alignment: .top) {
                    Text(kanji.k)
                        .font(.system(size: 96))
                        .textSelection(.enabled)
                    Spacer()
                    Button("Copy", systemImage: "doc.on.doc", action: onCopy)
                        .buttonStyle(.bordered)
                }
                Text(kanji.m.joined(separator: ", "))
                    .font(.title3.weight(.semibold))
                ReadingRow(label: "on", kanji: kanji.k, readings: kanji.on)
                ReadingRow(label: "kun", kanji: kanji.k, readings: kanji.kun)
                if !kanji.n.isEmpty {
                    LabeledRow(label: "names") {
                        Text(kanji.n.joined(separator: "、")).foregroundStyle(.secondary)
                    }
                }
                Text(metaLine(kanji))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .navigationTitle(kanji.k)
    }
}

private struct LabeledRow<Content: View>: View {
    let label: String
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label.uppercased())
                .font(.caption2)
                .tracking(1)
                .foregroundStyle(.secondary)
            content
        }
    }
}

/// Furigana stand-in: SwiftUI has no <ruby>, so each reading is kana / kanji+okurigana / romaji stacked.
private struct ReadingRow: View {
    let label: String
    let kanji: String
    let readings: [String]

    var body: some View {
        if !readings.isEmpty {
            LabeledRow(label: label) {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 76), alignment: .leading)], alignment: .leading, spacing: 10) {
                    ForEach(readings, id: \.self) { r in
                        let p = readingParts(r)
                        VStack(alignment: .leading, spacing: 0) {
                            Text(p.kana).font(.caption).foregroundStyle(.tint)
                            Text(p.prefix + kanji + p.okurigana + p.suffix).font(.title3)
                            Text(romaji(r)).font(.caption2).foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
    }
}
