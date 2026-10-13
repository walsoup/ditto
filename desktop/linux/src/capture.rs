use std::cell::RefCell;
use std::rc::Rc;

use adw::prelude::*;
use gtk::{gdk, glib};

use crate::app::Ditto;
use crate::widgets::{keycap, label};

fn is_modifier(key: gdk::Key) -> bool {
    let Some(name) = key.name() else { return false };
    ["Shift", "Control", "Alt", "Super", "Meta", "Hyper", "ISO_Level", "Caps_Lock", "Num_Lock"]
        .iter()
        .any(|prefix| name.starts_with(prefix))
}

/// Asks for a new global shortcut. GNOME grabs the current binding before any
/// app sees it, so the caller disables it while this is open.
pub fn open(ditto: &Rc<Ditto>, parent: Option<&gtk::Window>) {
    let title = label("Press the new shortcut", &["picker-title"]);
    title.set_xalign(0.5);
    let preview = keycap(&ditto.shortcut_label());
    preview.add_css_class("large");
    preview.set_halign(gtk::Align::Center);
    let hint = label("Esc cancels. Backspace turns the shortcut off.", &["hint"]);
    hint.set_xalign(0.5);
    hint.set_wrap(true);

    let body = gtk::Box::new(gtk::Orientation::Vertical, 18);
    body.set_margin_start(32);
    body.set_margin_end(32);
    body.set_margin_bottom(32);
    body.append(&title);
    body.append(&preview);
    body.append(&hint);

    let header = adw::HeaderBar::new();
    header.set_show_title(false);
    let content = gtk::Box::new(gtk::Orientation::Vertical, 0);
    content.append(&header);
    content.append(&body);

    let window = adw::Window::builder()
        .title("Shortcut")
        .modal(true)
        .resizable(false)
        .default_width(380)
        .content(&content)
        .build();
    window.add_css_class("ditto");
    window.set_transient_for(parent);

    // None keeps the old shortcut; Some("") turns it off.
    let chosen: Rc<RefCell<Option<String>>> = Rc::default();

    let keys = gtk::EventControllerKey::new();
    keys.set_propagation_phase(gtk::PropagationPhase::Capture);
    {
        let chosen = chosen.clone();
        let window = window.downgrade();
        keys.connect_key_pressed(move |_, key, _, state| {
            let Some(window) = window.upgrade() else { return glib::Propagation::Proceed };
            if is_modifier(key) {
                return glib::Propagation::Stop;
            }
            let mods = state & gtk::accelerator_get_default_mod_mask();
            if mods.is_empty() && key == gdk::Key::Escape {
                window.close();
                return glib::Propagation::Stop;
            }
            if mods.is_empty() && key == gdk::Key::BackSpace {
                chosen.replace(Some(String::new()));
                window.close();
                return glib::Propagation::Stop;
            }
            // A bare typing key (letters, Space, Enter, Tab) would hijack normal typing everywhere.
            if mods.is_empty() && key.to_unicode().is_some() {
                hint.set_label("Letters and numbers need Ctrl, Alt or Super. Function keys work alone.");
                return glib::Propagation::Stop;
            }
            let key = key.to_lower();
            if !gtk::accelerator_valid(key, mods) {
                return glib::Propagation::Stop;
            }
            preview.set_label(&gtk::accelerator_get_label(key, mods));
            chosen.replace(Some(gtk::accelerator_name(key, mods).to_string()));
            window.close();
            glib::Propagation::Stop
        });
    }
    window.add_controller(keys);

    {
        let ditto = ditto.clone();
        window.connect_close_request(move |_| {
            ditto.apply_shortcut(chosen.take());
            glib::Propagation::Proceed
        });
    }
    window.present();
}
