//! GNOME and desktop integration that goes through command-line tools.

use std::fs;
use std::io::{self, Write};
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::thread;

use ditto_core::gnome::{self, ENTRY_PATH, ENTRY_SCHEMA, KEYBINDINGS_SCHEMA};
use ditto_core::{paths, APP_ID};

fn gsettings(args: &[&str]) -> io::Result<String> {
    let output = Command::new("gsettings").args(args).stdin(Stdio::null()).output()?;
    if output.status.success() {
        Ok(String::from_utf8_lossy(&output.stdout).trim().to_string())
    } else {
        Err(io::Error::other(String::from_utf8_lossy(&output.stderr).trim().to_string()))
    }
}

fn exe() -> io::Result<String> {
    Ok(std::env::current_exe()?.to_string_lossy().into_owned())
}

/// Points a GNOME custom shortcut at `ditto --toggle`. An empty binding disables
/// it, which the shortcut picker needs so it can see the key itself.
pub fn install_shortcut(binding: &str) -> io::Result<()> {
    let current = gsettings(&["get", KEYBINDINGS_SCHEMA, "custom-keybindings"])?;
    let before = gnome::parse_string_list(&current);
    let after = gnome::with_entry(before.clone());
    if after != before {
        gsettings(&["set", KEYBINDINGS_SCHEMA, "custom-keybindings", &gnome::format_string_list(&after)])?;
    }
    let entry = format!("{ENTRY_SCHEMA}:{ENTRY_PATH}");
    gsettings(&["set", &entry, "name", &gnome::quote("Ditto: start or stop a voice note")])?;
    gsettings(&["set", &entry, "command", &gnome::quote(&gnome::command_for(&exe()?, "--toggle"))])?;
    gsettings(&["set", &entry, "binding", &gnome::quote(binding)])?;
    Ok(())
}

fn autostart_file() -> PathBuf {
    paths::config_home().join("autostart").join(format!("{APP_ID}.desktop"))
}

pub fn autostart_enabled() -> bool {
    autostart_file().exists()
}

pub fn set_autostart(enabled: bool) -> io::Result<()> {
    let file = autostart_file();
    if !enabled {
        return match fs::remove_file(&file) {
            Err(e) if e.kind() != io::ErrorKind::NotFound => Err(e),
            _ => Ok(()),
        };
    }
    if let Some(dir) = file.parent() {
        fs::create_dir_all(dir)?;
    }
    let exec = gnome::command_for(&exe()?, "--background");
    fs::write(
        file,
        format!(
            "[Desktop Entry]\nType=Application\nName=Ditto\nComment=Voice notes on a shortcut\n\
             Exec={exec}\nIcon={APP_ID}\nNoDisplay=true\nX-GNOME-Autostart-enabled=true\n"
        ),
    )
}

/// Used when none of our windows has focus, since Wayland only lets the focused
/// client set the clipboard.
pub fn wl_copy_uri(uri: &str) {
    let Ok(mut child) = Command::new("wl-copy")
        .args(["--type", "text/uri-list"])
        .stdin(Stdio::piped())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .spawn()
    else {
        return;
    };
    if let Some(mut stdin) = child.stdin.take() {
        let _ = stdin.write_all(format!("{uri}\r\n").as_bytes());
    }
    thread::spawn(move || {
        let _ = child.wait();
    });
}

pub fn play(file: &Path) -> io::Result<std::process::Child> {
    Command::new("ffplay")
        .args(["-nodisp", "-autoexit", "-loglevel", "quiet"])
        .arg(file)
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .spawn()
}
