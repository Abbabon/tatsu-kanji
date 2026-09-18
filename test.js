// node test.js
global.window = {};
require("./data.js");
const { romaji, load, search } = require("./search.js");
const assert = require("assert");
load(window.KANJI);

assert.equal(romaji("きょう"), "kyou");
assert.equal(romaji("ニチ"), "nichi");
assert.equal(romaji("た.べる"), "taberu");
assert.equal(romaji("まっちゃ"), "matcha");
assert.equal(romaji("がっこう"), "gakkou");
assert.equal(romaji("じゅう"), "juu");

const first = q => search(q)[0]?.k;
assert.equal(first("water"), "水");
assert.equal(first("みず"), "水");
assert.equal(first("ミズ"), "水");
assert.equal(first("mizu"), "水");
assert.equal(first("sun"), "日");
assert.equal(first("nichi"), "日");
assert.equal(first("taberu"), "食");
assert.deepEqual(search("日本語").map(e => e.k), ["日", "本", "語"]);
assert.ok(search("watr").slice(0, 3).some(e => e.k === "水"), "typo tolerance");
assert.ok(search("mountian").slice(0, 3).some(e => e.k === "山"), "typo tolerance");
assert.deepEqual(search("   "), []);
console.log("ok");
