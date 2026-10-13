use std::path::Path;

use gtk::{gdk, gio, glib};

use crate::system;

/// Puts the note on the clipboard as a file, the way Files does, so Ctrl+V in a
/// chat attaches it. `focused` says whether one of our windows holds keyboard
/// focus; without it Wayland ignores GTK's request and we go through wl-copy.
pub fn copy_file(file: &Path, focused: bool) {
    let uri = gio::File::for_path(file).uri().to_string();
    if !focused {
        system::wl_copy_uri(&uri);
        return;
    }
    let Some(display) = gdk::Display::default() else { return };
    let uri_list = glib::Bytes::from_owned(format!("{uri}\r\n").into_bytes());
    let nautilus = glib::Bytes::from_owned(format!("copy\n{uri}").into_bytes());
    let provider = gdk::ContentProvider::new_union(&[
        gdk::ContentProvider::for_bytes("text/uri-list", &uri_list),
        gdk::ContentProvider::for_bytes("x-special/gnome-copied-files", &nautilus),
    ]);
    if display.clipboard().set_content(Some(&provider)).is_err() {
        system::wl_copy_uri(&uri);
    }
}
