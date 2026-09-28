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
    // results and the query they belong to; filled off the main thread by .task(id: query)
    @State private var found: (query: String, kanji: [Kanji]) = ("", [])

    private var isBlank: Bool { query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

    var body: some View {
        NavigationSplitView {
            List(selection: $selection) {
                if isBlank {
                    recentSection
                } else if !found.kanji.isEmpty {
                    ForEach(found.kanji) { row($0) }
                } else if found.query == query {
                    noMatches
                }
            }
            .task(id: query) {
                let (q, s) = (query, searcher)
                let hits = await Task.detached { s.search(q) }.value
                if !Task.isCancelled { found = (q, hits) }
            }
            .navigationTitle("Tatsu")
            .searchable(text: $query, isPresented: $searching, prompt: "English, kana, romaji or kanji")
            .autocorrectionDisabled()
            #if os(iOS)
            .textInputAutocapitalization(.never)
            #endif
            // Mac / iPad hardware keyboard: typing while the list has focus goes to the search field
            .onKeyPress(phases: .down) { press in
                guard press.modifiers.isDisjoint(with: [.command, .control, .option]) else { return .ignored }
                if press.key == .delete, !query.isEmpty {
                    query.removeLast()
                } else if isTypable(press.characters) {
                    query.append(press.characters)
                } else {
                    return .ignored
                }
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
            // only record picks from search results: touching while browsing Recents
            // would re-sort the list under the selection
            if let k, !isBlank { Recent.touch(k, in: context) }
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
