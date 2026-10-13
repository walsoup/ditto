#!/usr/bin/env bash
# Builds Ditto for Linux and installs it for the current user (no sudo needed
# once the build packages are present).
set -euo pipefail
cd "$(dirname "$0")"

missing=()
for tool in cargo pkg-config pw-record ffmpeg wl-copy gsettings; do
  command -v "$tool" >/dev/null || missing+=("$tool")
done
if command -v pkg-config >/dev/null; then
  pkg-config --exists gtk4 libadwaita-1 || missing+=("gtk4/libadwaita headers")
fi
if ((${#missing[@]})); then
  echo "Missing: ${missing[*]}"
  echo "Install once with:"
  echo "  sudo apt install pkg-config libgtk-4-dev libadwaita-1-dev pipewire-bin ffmpeg wl-clipboard"
  exit 1
fi

cargo build --release

bin="$HOME/.local/bin/ditto"
apps="$HOME/.local/share/applications"
icons="$HOME/.local/share/icons/hicolor/scalable/apps"

pkill -x ditto 2>/dev/null || true
install -Dm755 target/release/ditto "$bin"
install -Dm644 data/com.walsoup.ditto.svg "$icons/com.walsoup.ditto.svg"
mkdir -p "$apps"
cat >"$apps/com.walsoup.ditto.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=Ditto
Comment=Voice notes on a shortcut: press, speak, press, paste
Exec=$bin
Icon=com.walsoup.ditto
Categories=AudioVideo;Audio;Utility;
Keywords=voice;note;record;clipboard;
StartupNotify=true
EOF

# Starting it once registers the GNOME shortcut.
setsid "$bin" --background >/dev/null 2>&1 < /dev/null &
echo "Installed. Press F4 anywhere to record, or open Ditto from the app grid."
