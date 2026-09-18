#!/usr/bin/env python3
"""Download KANJIDIC2 and emit data.js for the app. Run once; output is committed."""
import gzip, json, urllib.request, xml.etree.ElementTree as ET, os

URL = "http://www.edrdg.org/kanjidic/kanjidic2.xml.gz"
GZ = "kanjidic2.xml.gz"

if not os.path.exists(GZ):
    print("downloading", URL)
    urllib.request.urlretrieve(URL, GZ)

root = ET.parse(gzip.open(GZ)).getroot()
out = []
for ch in root.iter("character"):
    misc = ch.find("misc")
    rm = ch.find("reading_meaning")
    if rm is None:
        continue
    def readings(t): return [r.text for r in rm.iter("reading") if r.get("r_type") == t]
    meanings = [m.text for m in rm.iter("meaning") if m.get("m_lang") is None]
    if not meanings:
        continue
    out.append({
        "k": ch.findtext("literal"),
        "m": meanings,
        "on": readings("ja_on"),
        "kun": readings("ja_kun"),
        "n": [r.text for r in rm.iter("nanori")],
        "s": int(misc.findtext("stroke_count")),
        "g": int(misc.findtext("grade") or 0),
        "j": int(misc.findtext("jlpt") or 0),
        "f": int(misc.findtext("freq") or 0),
    })

# common kanji first: has freq rank (lower = more common), then by grade
out.sort(key=lambda e: (e["f"] or 9999, e["g"] or 99, e["s"]))
with open("data.js", "w") as f:
    f.write("window.KANJI=" + json.dumps(out, ensure_ascii=False, separators=(",", ":")) + ";\n")
print(len(out), "kanji written to data.js")
