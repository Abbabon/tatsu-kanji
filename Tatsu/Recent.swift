import Foundation
import SwiftData

/// A kanji the user opened or copied. Local to the device, never synced.
@Model
final class Recent {
    @Attribute(.unique) var kanji: String
    var opened: Date

    init(kanji: String, opened: Date) {
        self.kanji = kanji
        self.opened = opened
    }

    /// Insert or bump `kanji` to the top, then drop everything past the newest `keep`.
    static func touch(_ kanji: String, at date: Date = .now, in context: ModelContext, keep: Int = 100) {
        let same = FetchDescriptor<Recent>(predicate: #Predicate { $0.kanji == kanji })
        if let existing = try? context.fetch(same).first {
            existing.opened = date
        } else {
            context.insert(Recent(kanji: kanji, opened: date))
        }
        // save first: fetchOffset is ignored for unsaved inserts, which would trim everything
        try? context.save()
        var overflow = FetchDescriptor<Recent>(sortBy: [SortDescriptor(\.opened, order: .reverse)])
        overflow.fetchOffset = keep
        for old in (try? context.fetch(overflow)) ?? [] { context.delete(old) }
        try? context.save()
    }

    static func remove(_ kanji: String, in context: ModelContext) {
        let same = FetchDescriptor<Recent>(predicate: #Predicate { $0.kanji == kanji })
        for r in (try? context.fetch(same)) ?? [] { context.delete(r) }
        try? context.save()
    }

    static func clear(in context: ModelContext) {
        for r in (try? context.fetch(FetchDescriptor<Recent>())) ?? [] { context.delete(r) }
        try? context.save()
    }
}
