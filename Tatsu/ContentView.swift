import SwiftData
import SwiftUI

struct ContentView: View {
    let searcher: Searcher

    @Environment(\.modelContext) private var context
    @Query(sort: \Recent.opened, order: .reverse) private var recents: [Recent]
    @State private var query = ""
    @State private var searching = false
    @State private var selection: String?
    @State private var showAbout = false

    private var isBlank: Bool { query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

    var body: some View {
        NavigationSplitView {
            List(selection: $selection) {
                if isBlank {
                    recentSection
                } else {
                    let results = searcher.search(query)
                    if results.isEmpty {
                        noMatches
                    } else {
                        ForEach(results) { row($0) }
                    }
                }
            }
            .navigationTitle("Tatsu")
            .searchable(text: $query, isPresented: $searching, prompt: "English, kana, romaji or kanji")
            .autocorrectionDisabled()
            #if os(iOS)
            .textInputAutocapitalization(.never)
            #endif
            // Mac / iPad hardware keyboard: typing while the list has focus goes to the search field
            .onKeyPress(characters: .alphanumerics, phases: .down) { press in
                guard !press.modifiers.contains(.command) else { return .ignored }
                query.append(press.characters)
                searching = true
                return .handled
            }
            .toolbar {
                ToolbarItem {
                    Button("About", systemImage: "info.circle") { showAbout = true }
                }
            }
            .background {
                // Cmd+F focuses search
                Button("Find") { searching = true }
                    .keyboardShortcut("f", modifiers: .command)
                    .opacity(0)
            }
        } detail: {
            if let k = selection, let e = searcher.kanji(k) {
                KanjiDetail(kanji: e) { copy(e.k) }
            } else {
                Text("Pick a kanji").foregroundStyle(.secondary)
            }
        }
        .onChange(of: selection) { _, k in
            if let k { Recent.touch(k, in: context) }
        }
        .sheet(isPresented: $showAbout) { AboutView() }
    }

    @ViewBuilder private var recentSection: some View {
        // recents whose kanji vanished after a data update are skipped
        let items = recents.compactMap { searcher.kanji($0.kanji) }
        if items.isEmpty {
            Text("Type English, hiragana, katakana, romaji, or paste kanji.")
                .foregroundStyle(.secondary)
        } else {
            Section {
                ForEach(items) { row($0) }
            } header: {
                HStack {
                    Text("Recent")
                    Spacer()
                    Button("Clear") { Recent.clear(in: context) }
                        .font(.caption)
                }
            }
        }
    }

    @ViewBuilder private var noMatches: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("No matches.")
            #if os(iOS)
            Text("Can't type it? Add the Chinese – Handwriting keyboard in Settings › General › Keyboard › Keyboards, then draw the kanji.")
                .font(.footnote)
                .foregroundStyle(.secondary)
            #endif
        }
    }

    private func row(_ e: Kanji) -> some View {
        HStack(spacing: 12) {
            Text(e.k).font(.system(size: 40))
            VStack(alignment: .leading, spacing: 2) {
                Text(e.m.joined(separator: ", ")).lineLimit(2)
                Text((e.on + e.kun).joined(separator: "、"))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .tag(e.k)
        .contextMenu {
            Button("Copy", systemImage: "doc.on.doc") { copy(e.k) }
        }
    }

    private func copy(_ k: String) {
        copyToPasteboard(k)
        Recent.touch(k, in: context)
    }
}
