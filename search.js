// Search core. Plain script in the browser, CommonJS module under node (test.js).
const toHira = s => s.replace(/[ァ-ヶ]/g, c => String.fromCharCode(c.charCodeAt(0) - 0x60));

// kana -> wapuro romaji (what people actually type). Digraphs listed first so they win.
const ROMA = {};
("きゃkya きゅkyu きょkyo しゃsha しゅshu しょsho ちゃcha ちゅchu ちょcho にゃnya にゅnyu にょnyo " +
 "ひゃhya ひゅhyu ひょhyo みゃmya みゅmyu みょmyo りゃrya りゅryu りょryo ぎゃgya ぎゅgyu ぎょgyo " +
 "じゃja じゅju じょjo ぢゃja ぢゅju ぢょjo びゃbya びゅbyu びょbyo ぴゃpya ぴゅpyu ぴょpyo " +
 "あa いi うu えe おo かka きki くku けke こko さsa しshi すsu せse そso たta ちchi つtsu てte とto " +
 "なna にni ぬnu ねne のno はha ひhi ふfu へhe ほho まma みmi むmu めme もmo やya ゆyu よyo " +
 "らra りri るru れre ろro わwa ゐwi ゑwe をwo んn がga ぎgi ぐgu げge ごgo ざza じji ずzu ぜze ぞzo " +
 "だda ぢji づzu でde どdo ばba びbi ぶbu べbe ぼbo ぱpa ぴpi ぷpu ぺpe ぽpo ぁa ぃi ぅu ぇe ぉo ゔvu")
  .split(" ").forEach(t => { const k = t.match(/^[^a-z]+/)[0]; ROMA[k] = t.slice(k.length); });

function romaji(kana) {
  kana = toHira(kana).replace(/[.\-ー]/g, "");
  let out = "";
  for (let i = 0; i < kana.length; i++) {
    if (kana[i] === "っ") {
      const rest = romaji(kana.slice(i + 1));
      return out + (/^[aeiou]/.test(rest) ? "" : rest[0] === "c" ? "t" : rest[0] || "") + rest;
    }
    const two = ROMA[kana.slice(i, i + 2)];
    if (two) { out += two; i++; } else out += ROMA[kana[i]] ?? kana[i];
  }
  return out;
}

// edit distance <= 1 (insert/delete/substitute/adjacent swap)
function within1(a, b) {
  if (Math.abs(a.length - b.length) > 1) return false;
  let i = 0, j = 0, used = false;
  while (i < a.length && j < b.length) {
    if (a[i] === b[j]) { i++; j++; continue; }
    if (used) return false;
    used = true;
    if (a.length === b.length && a[i] === b[j + 1] && a[i + 1] === b[j]) { i += 2; j += 2; continue; }
    if (a.length > b.length) i++; else if (a.length < b.length) j++; else { i++; j++; }
  }
  return true;
}

function subseq(q, s) {
  let i = 0;
  for (const c of s) if (c === q[i]) i++;
  return i === q.length;
}

function score(q, s) {
  if (s === q) return 100;
  if (s.startsWith(q)) return 80;
  if ((" " + s).includes(" " + q)) return 70;
  if (s.includes(q)) return 55;
  if (q.length >= 4 && s.split(" ").some(w => within1(q, w))) return 50;
  if (q.length >= 3 && subseq(q, s)) return 30;
  return 0;
}

let DB = [], BY_KANJI = {};
function load(list) {
  DB = list;
  for (const e of DB) {
    e._m = e.m.map(m => m.toLowerCase());
    e._kana = [...e.on, ...e.kun].map(r => toHira(r).replace(/[.\-]/g, ""));
    e._roma = e._kana.map(romaji);
    BY_KANJI[e.k] = e;
  }
}

function search(q, limit = 60) {
  q = toHira(q.trim().toLowerCase());
  if (!q) return [];
  const cjk = [...q].filter(c => BY_KANJI[c]);
  if (cjk.length) return [...new Set(cjk)].map(c => BY_KANJI[c]);
  const isKana = /^[ぁ-ゟ]+$/.test(q);
  const hits = [];
  for (const e of DB) {
    let s = 0;
    const fields = isKana ? e._kana : e._m.concat(e._roma);
    for (const f of fields) if ((s = Math.max(s, score(q, f))) === 100) break;
    if (s) hits.push([s, e]);
  }
  hits.sort((a, b) => b[0] - a[0] || (a[1].f || 9999) - (b[1].f || 9999));
  return hits.slice(0, limit).map(h => h[1]);
}

if (typeof module !== "undefined") module.exports = { toHira, romaji, load, search };
