#!/bin/sh
# Builds Tatsu.app next to this script. Requires Xcode command line tools (swiftc).
# ./make_app.sh install   also copies it into /Applications
set -e
cd "$(dirname "$0")"
[ -f data.js ] || python3 build_data.py
APP=Tatsu.app
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp index.html search.js data.js logo.svg "$APP/Contents/Resources/"

# icon: logo.svg -> padded png (macOS icons sit inside ~80% of the canvas) -> .icns
TMP=$(mktemp -d)
sed 's/viewBox="0 0 512 512"/viewBox="-62 -62 636 636"/' logo.svg > "$TMP/icon.svg"
qlmanage -t -s 1024 -o "$TMP" "$TMP/icon.svg" >/dev/null 2>&1
mkdir -p "$TMP/AppIcon.iconset"
for s in 16 32 128 256 512; do
  sips -z $s $s "$TMP/icon.svg.png" --out "$TMP/AppIcon.iconset/icon_${s}x${s}.png" >/dev/null
  sips -z $((s*2)) $((s*2)) "$TMP/icon.svg.png" --out "$TMP/AppIcon.iconset/icon_${s}x${s}@2x.png" >/dev/null
done
iconutil -c icns "$TMP/AppIcon.iconset" -o "$APP/Contents/Resources/AppIcon.icns"
rm -rf "$TMP"

cat > "$APP/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>CFBundleExecutable</key><string>Tatsu</string>
  <key>CFBundleIdentifier</key><string>com.abbabon.tatsu</string>
  <key>CFBundleName</key><string>Tatsu</string>
  <key>CFBundleIconFile</key><string>AppIcon</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleShortVersionString</key><string>1.0</string>
  <key>LSMinimumSystemVersion</key><string>12.0</string>
  <key>NSHighResolutionCapable</key><true/>
</dict></plist>
PLIST
swiftc -O main.swift -o "$APP/Contents/MacOS/Tatsu"
codesign --force --sign - "$APP" >/dev/null 2>&1 || true
echo "built $APP"
if [ "$1" = install ]; then
  rm -rf "/Applications/$APP" && cp -R "$APP" /Applications/ && echo "installed to /Applications/$APP"
fi
