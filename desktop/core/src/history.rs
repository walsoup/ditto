use std::fs;
use std::io;
use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};

use crate::paths;
use crate::voice::{Format, Voice};

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct Note {
    pub file: PathBuf,
    /// Unix seconds.
    pub created: i64,
    pub duration_ms: u64,
    #[serde(default)]
    pub voice: Voice,
    #[serde(default)]
    pub format: Format,
}

impl Note {
    pub fn new(file: PathBuf, duration_ms: u64, voice: Voice, format: Format) -> Self {
        let created =
            SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs() as i64).unwrap_or_default();
        Self { file, created, duration_ms, voice, format }
    }
}

/// Newest-first list of saved notes, persisted as JSON next to the audio files.
pub struct History {
    path: PathBuf,
    notes: Vec<Note>,
}

impl History {
    pub fn load() -> Self {
        Self::load_from(paths::data_dir().join("history.json"))
    }

    pub fn load_from(path: PathBuf) -> Self {
        let mut notes: Vec<Note> =
            fs::read_to_string(&path).ok().and_then(|s| serde_json::from_str(&s).ok()).unwrap_or_default();
        notes.retain(|n| n.file.exists());
        Self { path, notes }
    }

    pub fn notes(&self) -> &[Note] {
        &self.notes
    }

    pub fn add(&mut self, note: Note) -> io::Result<()> {
        self.notes.insert(0, note);
        self.save()
    }

    /// Forgets the note and deletes its audio file.
    pub fn remove(&mut self, file: &Path) -> io::Result<()> {
        self.notes.retain(|n| n.file != file);
        match fs::remove_file(file) {
            Err(e) if e.kind() != io::ErrorKind::NotFound => return Err(e),
            _ => {}
        }
        self.save()
    }

    fn save(&self) -> io::Result<()> {
        if let Some(dir) = self.path.parent() {
            fs::create_dir_all(dir)?;
        }
        fs::write(&self.path, serde_json::to_string_pretty(&self.notes)?)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn add_persists_newest_first_and_remove_deletes_the_file() {
        let dir = std::env::temp_dir().join(format!("ditto-history-{}", std::process::id()));
        fs::create_dir_all(&dir).unwrap();
        let path = dir.join("history.json");
        let a = dir.join("a.ogg");
        let b = dir.join("b.ogg");
        fs::write(&a, b"a").unwrap();
        fs::write(&b, b"b").unwrap();

        let mut history = History::load_from(path.clone());
        history.add(Note::new(a.clone(), 1_000, Voice::Raw, Format::Ogg)).unwrap();
        history.add(Note::new(b.clone(), 2_000, Voice::Studio, Format::Ogg)).unwrap();

        let reloaded = History::load_from(path.clone());
        let files: Vec<_> = reloaded.notes().iter().map(|n| n.file.clone()).collect();
        assert_eq!(files, [b.clone(), a.clone()]);

        let mut history = reloaded;
        history.remove(&b).unwrap();
        assert!(!b.exists());
        assert_eq!(History::load_from(path).notes().len(), 1);
        fs::remove_dir_all(dir).unwrap();
    }

    #[test]
    fn notes_whose_file_vanished_are_dropped_on_load() {
        let dir = std::env::temp_dir().join(format!("ditto-history-gone-{}", std::process::id()));
        fs::create_dir_all(&dir).unwrap();
        let path = dir.join("history.json");
        let gone = Note::new(dir.join("gone.ogg"), 500, Voice::Raw, Format::Ogg);
        fs::write(&path, serde_json::to_string(&vec![gone]).unwrap()).unwrap();
        assert!(History::load_from(path).notes().is_empty());
        fs::remove_dir_all(dir).unwrap();
    }
}
