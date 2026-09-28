import Testing
@testable import Tatsu

struct DataTests {
    @Test func bundledDataLoads() throws {
        let all = try Kanji.loadBundled()
        // 10,384 in the current KANJIDIC2 build; the exact count changes when build_data.py is re-run
        #expect(all.count > 10_000)
        #expect(all[0].k == "日")
        let water = try #require(all.first { $0.k == "水" })
        #expect(water.m.contains("water"))
        #expect(water.kun.contains("みず"))
        #expect(water.s == 4)
    }
}
