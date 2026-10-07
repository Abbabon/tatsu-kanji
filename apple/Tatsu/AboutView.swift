import SwiftUI

struct AboutView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("Tatsu · 断 · to cut off, to sever")
                    Text("Offline kanji lookup. No account, no network.")
                        .foregroundStyle(.secondary)
                }
                Section("Kanji data") {
                    Text("KANJIDIC2 by the Electronic Dictionary Research and Development Group, used under CC BY-SA 4.0.")
                    Link("KANJIDIC Project", destination: URL(string: "https://www.edrdg.org/wiki/index.php/KANJIDIC_Project")!)
                    Link("CC BY-SA 4.0 license", destination: URL(string: "https://creativecommons.org/licenses/by-sa/4.0/")!)
                }
                Section("App") {
                    Text("App code is MIT licensed.")
                    Link("Source code", destination: URL(string: "https://github.com/Abbabon/tatsu-kanji")!)
                }
            }
            .navigationTitle("About")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        #if os(macOS)
        .frame(minWidth: 420, minHeight: 360)
        #endif
    }
}
