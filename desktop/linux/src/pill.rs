use std::rc::Rc;
use std::time::Duration;

use adw::prelude::*;
use gtk::{gdk, glib};

use ditto_core::level::clock;

use crate::app::Ditto;
use crate::widgets::{self, keycap, label};

/// The small capsule shown while recording from another app. It is a real
/// window, so it takes focus while open and hands it back to the chat on close.
pub struct Pill {
    window: gtk::Window,
    timer: gtk::Label,
    meter: gtk::DrawingArea,
    wave: gtk::DrawingArea,
    done: gtk::Button,
    discard: gtk::Button,
}

impl Pill {
    pub fn open(ditto: &Rc<Ditto>) -> Self {
        let capsule = gtk::Box::new(gtk::Orientation::Horizontal, 14);
        capsule.add_css_class("dock");
        capsule.add_css_class("live");
        capsule.set_width_request(460);

        let timer = label("00:00", &["timer", "live"]);
        let meter = widgets::level_meter(ditto.levels());
        let wave = widgets::waveform(ditto.levels());
        wave.set_width_request(120);

        let hint = gtk::Box::new(gtk::Orientation::Horizontal, 6);
        hint.set_valign(gtk::Align::Center);
        hint.append(&keycap(&ditto.shortcut_label()));
        hint.append(&label("to stop", &["hint"]));

        let discard = widgets::icon_button("window-close-symbolic", "Discard note");
        discard.set_valign(gtk::Align::Center);
        let done = widgets::pill_button("Done");
        done.add_css_class("stop");

        capsule.append(&timer);
        capsule.append(&meter);
        capsule.append(&wave);
        capsule.append(&hint);
        capsule.append(&discard);
        capsule.append(&done);

        let window = gtk::Window::builder()
            .application(ditto.app())
            .title("Ditto: recording")
            .decorated(false)
            .resizable(false)
            .child(&capsule)
            .build();
        window.add_css_class("pill-window");

        {
            let ditto = ditto.clone();
            done.connect_clicked(move |_| ditto.toggle());
        }
        {
            let ditto = ditto.clone();
            discard.connect_clicked(move |_| ditto.cancel());
        }
        let keys = gtk::EventControllerKey::new();
        {
            let ditto = ditto.clone();
            keys.connect_key_pressed(move |_, key, _, _| {
                if key == gdk::Key::Escape || key == gdk::Key::Return {
                    ditto.toggle();
                    return glib::Propagation::Stop;
                }
                glib::Propagation::Proceed
            });
        }
        window.add_controller(keys);
        {
            let ditto = ditto.clone();
            window.connect_close_request(move |_| {
                let ditto = ditto.clone();
                glib::idle_add_local_once(move || ditto.cancel());
                glib::Propagation::Stop
            });
        }

        window.present();
        done.grab_focus();
        Self { window, timer, meter, wave, done, discard }
    }

    pub fn update(&self, elapsed: Duration) {
        self.timer.set_label(&clock(elapsed));
        self.meter.queue_draw();
        self.wave.queue_draw();
    }

    pub fn set_saving(&self) {
        self.discard.set_visible(false);
        widgets::set_pill_label(&self.done, "Saving…");
        self.done.set_sensitive(false);
    }

    pub fn is_active(&self) -> bool {
        self.window.is_active()
    }

    pub fn close(&self) {
        self.window.destroy();
    }
}
