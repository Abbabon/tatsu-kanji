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
    @State private var keyMonitor: Any?
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
                #if os(macOS)
                ContentUnavailableView("Start typing to search", systemImage: "magnifyingglass",
                                       description: Text("English, kana, romaji, or kanji"))
                #else
                ContentUnavailableView("Search for a kanji", systemImage: "magnifyingglass",
                                       description: Text("Tap the search field and type English, kana, romaji, or kanji"))
                #endif
            }
        }
        // iPad hardware keyboard: typing anywhere goes to the search field; up/down move the
        // selection when no text field has them. On the Mac installKeyMonitor gets typed keys
        // first, so this only handles up/down there.
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
        .onAppear { installKeyMonitor() }
        .onDisappear { keyMonitor.map(NSEvent.removeMonitor); keyMonitor = nil }
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
                        .padding(.trailing, 8)
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
        // fill the whole row so the tap area isn't just the text's intrinsic width
        .frame(maxWidth: .infinity, alignment: .leading)
        #if os(iOS)
        // iOS insets are outside the view (untappable by our gesture); move them inside it
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .listRowInsets(EdgeInsets())
        #endif
        .contentShape(Rectangle())
        .tag(e.k)
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
    /// Type-anywhere on the Mac: SwiftUI's onKeyPress only sees keys when a focusable view
    /// has focus (at launch the window itself is first responder, so it sees nothing), and
    /// `searching = true` doesn't refocus a field it considers presented, so route key-downs
    /// here first. Typed keys go
    /// to the search field itself (keeping input methods working); up/down in the field
    /// step the results instead of moving the caret.
    private func installKeyMonitor() {
        guard keyMonitor == nil else { return }
        keyMonitor = NSEvent.addLocalMonitorForEvents(matching: .keyDown) { event in
            guard let window = event.window else { return event }
            let focus: KeyFocus = switch window.firstResponder {
            case let t as NSTextView where t.isFieldEditor && t.delegate is NSSearchField: .searchField
            case is NSText, is NSTextField: .textInput
            default: .other
            }
            let flags = event.modifierFlags
            switch keyRoute(keyCode: event.keyCode, characters: event.characters ?? "",
                            shortcut: !flags.isDisjoint(with: [.command, .control, .option]),
                            shift: flags.contains(.shift), focus: focus, queryEmpty: query.isEmpty) {
            case .pass:
                return event
            case .step(let delta):
                selection = stepSelection(selection, in: visibleIDs, by: delta)
                return nil
            case .toSearch:
                let field = window.toolbar?.items.lazy.compactMap { ($0 as? NSSearchToolbarItem)?.searchField }.first
                guard let field, window.makeFirstResponder(field), let editor = field.currentEditor() else { return event }
                // focusing selects all; put the caret at the end, then let AppKit deliver the key to the field
                editor.selectedRange = NSRange(location: (editor.string as NSString).length, length: 0)
                return event
            }
        }
    }
    #endif

    private func copy(_ k: String) {
        copyToPasteboard(k)
        Recent.touch(k, in: context)
    }
}
