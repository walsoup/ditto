use std::fs;
use std::io;
use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};

use crate::paths;
use crate::voice::{Format, Voice};

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct Config {
    /// GTK accelerator syntax, e.g. `F4` or `<Control><Shift>r`.
    pub shortcut: String,
    pub voice: Voice,
    pub format: Format,
}

impl Default for Config {
    fn default() -> Self {
        Self { shortcut: "F4".into(), voice: Voice::Raw, format: Format::Ogg }
    }
}

impl Config {
    pub fn path() -> PathBuf {
        paths::config_dir().join("config.json")
    }

    pub fn load() -> Self {
        Self::load_from(&Self::path())
    }

    pub fn load_from(path: &Path) -> Self {
        fs::read_to_string(path).ok().and_then(|s| serde_json::from_str(&s).ok()).unwrap_or_default()
    }

    pub fn save(&self) -> io::Result<()> {
        self.save_to(&Self::path())
    }

    pub fn save_to(&self, path: &Path) -> io::Result<()> {
        if let Some(dir) = path.parent() {
            fs::create_dir_all(dir)?;
        }
        fs::write(path, serde_json::to_string_pretty(self)?)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn missing_or_partial_file_falls_back_to_defaults() {
        let dir = std::env::temp_dir().join(format!("ditto-config-{}", std::process::id()));
        let path = dir.join("config.json");
        assert_eq!(Config::load_from(&path), Config::default());

        fs::create_dir_all(&dir).unwrap();
        fs::write(&path, r#"{ "voice": "Studio" }"#).unwrap();
        let loaded = Config::load_from(&path);
        assert_eq!(loaded.voice, Voice::Studio);
        assert_eq!(loaded.shortcut, "F4");

        let changed = Config { shortcut: "<Control>F9".into(), ..loaded };
        changed.save_to(&path).unwrap();
        assert_eq!(Config::load_from(&path), changed);
        fs::remove_dir_all(dir).unwrap();
    }
}
