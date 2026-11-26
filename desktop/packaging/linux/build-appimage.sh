#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

VERSION="${1:-$(grep '^APP_VERSION_NAME=' "$REPO_ROOT/gradle.properties" | cut -d= -f2 | tr -d '[:space:]')}"
VERSION="${VERSION#v}"
ARCH="${ARCH:-x86_64}"

RELEASE_IMAGE_DIR="$REPO_ROOT/desktop/build/compose/binaries/main-release/app/Tonarc"
DEBUG_IMAGE_DIR="$REPO_ROOT/desktop/build/compose/binaries/main/app/Tonarc"

if [[ -d "$RELEASE_IMAGE_DIR" ]]; then
  SOURCE_IMAGE_DIR="$RELEASE_IMAGE_DIR"
elif [[ -d "$DEBUG_IMAGE_DIR" ]]; then
  SOURCE_IMAGE_DIR="$DEBUG_IMAGE_DIR"
else
  echo "Error: Compose desktop distributable not found." >&2
  echo "Run ./gradlew :desktop:createReleaseDistributable first." >&2
  exit 1
fi

OUT_DIR="$REPO_ROOT/desktop/build/appimage"
APPDIR="$OUT_DIR/Tonarc.AppDir"
TOOLS_DIR="$REPO_ROOT/desktop/build/tools"
APPIMAGETOOL="$TOOLS_DIR/appimagetool-${ARCH}.AppImage"
OUTPUT_APPIMAGE="$OUT_DIR/Tonarc-${VERSION}-linux-${ARCH}.AppImage"

rm -rf "$APPDIR"
mkdir -p \
  "$APPDIR/usr/bin" \
  "$APPDIR/usr/lib" \
  "$APPDIR/usr/share/applications" \
  "$APPDIR/usr/share/icons/hicolor/512x512/apps" \
  "$APPDIR/usr/share/metainfo" \
  "$TOOLS_DIR"

# Copy jpackage app image contents (bin/, lib/) into AppDir/usr/
cp -a "$SOURCE_IMAGE_DIR"/* "$APPDIR/usr/"

# Ensure launcher symlink matches Exec=tonarc
if [[ -x "$APPDIR/usr/bin/Tonarc" && ! -e "$APPDIR/usr/bin/tonarc" ]]; then
  ln -s Tonarc "$APPDIR/usr/bin/tonarc"
fi

# Install AppRun, desktop entry, icon, and AppStream metadata
install -m 0755 "$SCRIPT_DIR/AppRun" "$APPDIR/AppRun"
install -m 0644 "$SCRIPT_DIR/com.quietrays.tonarc.desktop" "$APPDIR/com.quietrays.tonarc.desktop"
install -m 0644 "$SCRIPT_DIR/com.quietrays.tonarc.desktop" "$APPDIR/usr/share/applications/com.quietrays.tonarc.desktop"
install -m 0644 "$SCRIPT_DIR/com.quietrays.tonarc.metainfo.xml" "$APPDIR/usr/share/metainfo/com.quietrays.tonarc.metainfo.xml"
install -m 0644 "$REPO_ROOT/assets/icon.png" "$APPDIR/tonarc.png"
install -m 0644 "$REPO_ROOT/assets/icon.png" "$APPDIR/.DirIcon"
install -m 0644 "$REPO_ROOT/assets/icon.png" "$APPDIR/usr/share/icons/hicolor/512x512/apps/tonarc.png"

if command -v desktop-file-validate >/dev/null 2>&1; then
  desktop-file-validate "$APPDIR/com.quietrays.tonarc.desktop"
fi

if [[ ! -x "$APPIMAGETOOL" ]]; then
  APPIMAGETOOL_URL="https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-${ARCH}.AppImage"
  echo "Downloading appimagetool from $APPIMAGETOOL_URL ..."
  curl -fL --retry 3 --retry-delay 2 "$APPIMAGETOOL_URL" -o "$APPIMAGETOOL"
  chmod +x "$APPIMAGETOOL"
fi

rm -f "$OUTPUT_APPIMAGE" "${OUTPUT_APPIMAGE}.sha256"
VERSION="$VERSION" ARCH="$ARCH" "$APPIMAGETOOL" --appimage-extract-and-run --no-appstream "$APPDIR" "$OUTPUT_APPIMAGE"
chmod +x "$OUTPUT_APPIMAGE"

(
  cd "$OUT_DIR"
  sha256sum "$(basename "$OUTPUT_APPIMAGE")" > "$(basename "$OUTPUT_APPIMAGE").sha256"
)

echo "Built AppImage: $OUTPUT_APPIMAGE"
ls -lh "$OUTPUT_APPIMAGE" "${OUTPUT_APPIMAGE}.sha256"
