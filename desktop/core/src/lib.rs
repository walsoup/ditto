//! Everything Ditto for desktop does that doesn't depend on a GUI toolkit or an OS
//! audio stack. Platform crates (`desktop/linux`, later Windows and macOS) supply
//! capture, the global shortcut, the clipboard and the window.

pub mod config;
pub mod gnome;
pub mod history;
pub mod level;
pub mod paths;
pub mod toggle;
pub mod voice;

pub const APP_ID: &str = "com.walsoup.ditto";

/// Capture format every platform recorder must deliver: signed 16-bit little-endian mono.
pub const SAMPLE_RATE: u32 = 48_000;
pub const BYTES_PER_SECOND: u64 = SAMPLE_RATE as u64 * 2;
