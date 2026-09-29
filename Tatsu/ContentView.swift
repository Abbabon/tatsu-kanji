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
    #if os(macOS)
    @State private var arrowMonitor: Any?
    #endif
    // Recent order shown; frozen while browsing so a click doesn't re-sort under the cursor,
    // refreshed whenever the recents are shown again (query cleared)
    @State private var recentOrder: [String] = []
    // results and the query they belong to; filled off the main thread by .task(id: query)
    @State private var found: (query: String, kanji: [Kanji]) = ("", [])

    private var isBlank: Bool { query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    private var visibleIDs: [String] { isBlank ? recentOrder : found.kanji.map(\.k) }

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
            .onSubmit(of: .search) { enter() }
            .autocorrectionDisabled()
            #if os(iOS)
            .textInputAutocapitalization(.never)
            #endif
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
        // Mac / iPad hardware keyboard: typing anywhere in the window goes to the search field,
        // and up/down move the selection when no text field has them (Mac search field: see
        // installArrowMonitor)
        .onKeyPress(phases: [.down, .repeat]) { press in
            guard press.modifiers.isDisjoint(with: [.command, .control, .option]) else { return .ignored }
            if press.key == .downArrow || press.key == .upArrow {
                selection = stepSelection(selection, in: visibleIDs, by: press.key == .downArrow ? 1 : -1)
                return .handled
            }
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
        .task { searching = true }  // launch: focus the search field
        .onAppear { syncRecents(resort: true) }
        #if os(macOS)
        .onAppear { installArrowMonitor() }
        .onDisappear { arrowMonitor.map(NSEvent.removeMonitor); arrowMonitor = nil }
        #endif
        .onChange(of: isBlank) { _, blank in if blank { syncRecents(resort: true) } }
        .onChange(of: recents.map(\.kanji)) { syncRecents(resort: false) }
        .sheet(isPresented: $showAbout) { AboutView() }
    }

    @ViewBuilder private var recentSection: some View {
        // recents whose kanji vanished after a data update are skipped
        let items = recentOrder.compactMap { searcher.kanji($0) }
        if items.isEmpty {
            Text("Type English, hiragana, katakana, romaji, or paste kanji.")
                .foregroundStyle(.secondary)
        } else {
            Section {
                ForEach(items) { row($0, inRecents: true) }
            } header: {
                HStack {
                    Text("Recent")
                    Spacer()
                    Button("Clear") {
                        Recent.clear(in: context)
                        selection = nil
                        searching = true
                    }
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

    private func row(_ e: Kanji, inRecents: Bool = false) -> some View {
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
        .contentShape(Rectangle())
        // clicks/taps are explicit picks; arrow-key selection changes are not recorded
        .simultaneousGesture(TapGesture().onEnded { Recent.touch(e.k, in: context) })
        .contextMenu {
            Button("Copy", systemImage: "doc.on.doc") { copy(e.k) }
            if inRecents {
                Button("Remove from History", systemImage: "trash") {
                    Recent.remove(e.k, in: context)
                    if selection == e.k { selection = nil }
                }
            }
        }
    }

    /// Return: record the current pick, or pick (and record) the first result.
    private func enter() {
        guard let k = selection ?? visibleIDs.first else { return }
        selection = k
        Recent.touch(k, in: context)
    }

    /// Keep the shown Recent order in step with the store. Without `resort`, existing
    /// entries keep their place and new ones go on top.
    private func syncRecents(resort: Bool) {
        let latest = recents.map(\.kanji)
        recentOrder = resort ? latest : latest.filter { !recentOrder.contains($0) } + recentOrder.filter(latest.contains)
    }

    #if os(macOS)
    /// The search field's field editor (AppKit) eats up/down as caret moves before SwiftUI's
    /// onKeyPress sees them, so catch them first — only while that field editor has focus.
    private func installArrowMonitor() {
        guard arrowMonitor == nil else { return }
        arrowMonitor = NSEvent.addLocalMonitorForEvents(matching: .keyDown) { event in
            guard event.keyCode == 125 || event.keyCode == 126,  // down, up
                  event.modifierFlags.isDisjoint(with: [.command, .control, .option, .shift]),
                  let editor = event.window?.firstResponder as? NSTextView,
                  editor.isFieldEditor, editor.delegate is NSSearchField
            else { return event }
            selection = stepSelection(selection, in: visibleIDs, by: event.keyCode == 125 ? 1 : -1)
            return nil
        }
    }
    #endif

    private func copy(_ k: String) {
        copyToPasteboard(k)
        Recent.touch(k, in: context)
    }
}
