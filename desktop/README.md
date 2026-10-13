# Ditto for desktop

Press a key anywhere, speak, press it again: the voice note is copied as a file, ready for Ctrl+V in any chat. Same idea as the Android app, same design language.

## Layout

| Crate | What it holds |
|---|---|
| `core/` | Platform-neutral logic: config, note history, voices and ffmpeg encoding, level meter, toggle state, GNOME keybinding text. No GUI or OS audio dependencies. |
| `linux/` | GNOME app (Rust, GTK 4.14, libadwaita 1.5): PipeWire capture, global shortcut through `gsettings`, clipboard, main window, floating recording pill. |

A Windows or macOS port adds a sibling crate that depends on `core` and supplies capture, the global shortcut, the clipboard and the window.

## Linux

Runtime tools: `pw-record` (PipeWire), `ffmpeg`, `wl-copy`, `gsettings`.

```bash
sudo apt install pkg-config libgtk-4-dev libadwaita-1-dev   # once, for building
./linux/install.sh
```

The installer builds a release binary to `~/.local/bin/ditto`, adds a launcher, and starts Ditto in the background. Ditto then binds **F4** to `ditto --toggle` as a GNOME custom shortcut. Change it from the Shortcut row in the window.

## Tests

```bash
cd core && cargo test
```

MIT License, walsoup.
