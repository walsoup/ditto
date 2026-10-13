use std::collections::HashMap;

use adw::prelude::*;
use gtk::{gio, glib};

use ditto_core::APP_ID;

/// Shows a transient desktop notification. Unlike a window it never takes focus
/// away from the chat the user is about to paste into. `replaces` is the id of
/// the previous notification so a new one swaps it in place.
pub fn send(summary: &str, body: &str, replaces: u32, on_shown: impl FnOnce(u32) + 'static) {
    let Ok(bus) = gio::bus_get_sync(gio::BusType::Session, gio::Cancellable::NONE) else { return };
    let mut hints: HashMap<String, glib::Variant> = HashMap::new();
    hints.insert("transient".into(), true.to_variant());
    hints.insert("desktop-entry".into(), APP_ID.to_variant());
    hints.insert("urgency".into(), 0u8.to_variant());
    let params = (
        "Ditto".to_string(),
        replaces,
        "audio-input-microphone-symbolic".to_string(),
        summary.to_string(),
        body.to_string(),
        Vec::<String>::new(),
        hints,
        3_000i32,
    )
        .to_variant();
    bus.call(
        Some("org.freedesktop.Notifications"),
        "/org/freedesktop/Notifications",
        "org.freedesktop.Notifications",
        "Notify",
        Some(&params),
        glib::VariantTy::new("(u)").ok(),
        gio::DBusCallFlags::NONE,
        -1,
        gio::Cancellable::NONE,
        move |reply| {
            if let Some((id,)) = reply.ok().and_then(|v| v.get::<(u32,)>()) {
                on_shown(id);
            }
        },
    );
}
