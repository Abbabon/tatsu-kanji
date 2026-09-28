import Foundation
import SwiftData
import Testing
@testable import Tatsu

@MainActor
struct RecentTests {
    let container: ModelContainer  // held so the in-memory store lives as long as the test
    var context: ModelContext { container.mainContext }

    init() throws {
        container = try ModelContainer(for: Recent.self, configurations: ModelConfiguration(isStoredInMemoryOnly: true))
    }

    func all() throws -> [String] {
        try context.fetch(FetchDescriptor<Recent>(sortBy: [SortDescriptor(\.opened, order: .reverse)])).map(\.kanji)
    }

    @Test func newestFirst() throws {
        Recent.touch("水", at: Date(timeIntervalSince1970: 1), in: context)
        Recent.touch("日", at: Date(timeIntervalSince1970: 2), in: context)
        #expect(try all() == ["日", "水"])
    }

    @Test func touchingAgainMovesToTopWithoutDuplicate() throws {
        Recent.touch("水", at: Date(timeIntervalSince1970: 1), in: context)
        Recent.touch("日", at: Date(timeIntervalSince1970: 2), in: context)
        Recent.touch("水", at: Date(timeIntervalSince1970: 3), in: context)
        #expect(try all() == ["水", "日"])
    }

    @Test func keepsOnlyNewestHundred() throws {
        // 101 distinct CJK characters starting at 一 (U+4E00)
        let chars = (0..<101).map { String(Unicode.Scalar(0x4E00 + $0)!) }
        for (i, c) in chars.enumerated() {
            Recent.touch(c, at: Date(timeIntervalSince1970: Double(i)), in: context)
        }
        let kept = try all()
        #expect(kept.count == 100)
        #expect(kept.first == chars[100])
        #expect(!kept.contains(chars[0]))
    }

    @Test func clearRemovesEverything() throws {
        Recent.touch("水", in: context)
        Recent.touch("日", in: context)
        Recent.clear(in: context)
        #expect(try all().isEmpty)
    }
}
