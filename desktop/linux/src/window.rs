use std::rc::Rc;
use std::time::Duration;

use adw::prelude::*;
use gtk::{gio, glib};

use ditto_core::history::Note;
use ditto_core::level::clock;
use ditto_core::voice::{Format, Voice};

use crate::app::Ditto;
use crate::system;
use crate::widgets::{self, hairline, keycap, label, line};

pub struct MainWindow {
    pub window: adw::ApplicationWindow,
    toasts: adw::ToastOverlay,
    lede: gtk::Label,
    shortcut_cap: gtk::Label,
    list: gtk::ListBox,
    recent: gtk::Stack,
    count: gtk::Label,
    dock: gtk::Box,
    timer: gtk::Label,
    meter: gtk::DrawingArea,
    middle: gtk::Stack,
    hint: gtk::Label,
    wave: gtk::DrawingArea,
    record: gtk::Button,
    discard: gtk::Button,
}

fn today_overline() -> String {
    glib::DateTime::now_local()
        .and_then(|d| d.format("%A, %-d %B"))
        .map(|s| s.to_uppercase())
        .unwrap_or_default()
}

fn same_day(a: &glib::DateTime, b: &glib::DateTime) -> bool {
    a.ymd() == b.ymd()
}

fn track_subtitle(note: &Note) -> String {
    let Ok(when) = glib::DateTime::from_unix_local(note.created) else {
        return note.voice.label().to_string();
    };
    let now = glib::DateTime::now_local().ok();
    let yesterday = now.as_ref().and_then(|n| n.add_days(-1).ok());
    let day = if now.as_ref().is_some_and(|n| same_day(n, &when)) {
        "Today".to_string()
    } else if yesterday.as_ref().is_some_and(|y| same_day(y, &when)) {
        "Yesterday".to_string()
    } else {
        when.format("%-d %b").map(|s| s.to_string()).unwrap_or_default()
    };
    let time = when.format("%H:%M").map(|s| s.to_string()).unwrap_or_default();
    format!("{day} · {time} · {}", note.voice.label())
}

fn dropdown<T: Copy + PartialEq + 'static>(
    options: &'static [T],
    name: fn(T) -> &'static str,
    selected: T,
    on_change: impl Fn(T) + 'static,
) -> gtk::DropDown {
    let names: Vec<&str> = options.iter().map(|o| name(*o)).collect();
    let dropdown = gtk::DropDown::from_strings(&names);
    dropdown.add_css_class("line-dropdown");
    let index = options.iter().position(|o| *o == selected).unwrap_or(0);
    dropdown.set_selected(index as u32);
    dropdown.connect_selected_notify(move |d| {
        if let Some(value) = options.get(d.selected() as usize) {
            on_change(*value);
        }
    });
    dropdown
}

impl MainWindow {
    pub fn new(ditto: &Rc<Ditto>) -> Rc<Self> {
        let config = ditto.config();

        let window = adw::ApplicationWindow::builder()
            .application(ditto.app())
            .title("Ditto")
            .default_width(900)
            .default_height(600)
            .width_request(720)
            .height_request(480)
            .hide_on_close(true)
            .build();
        window.add_css_class("ditto");

        let header = adw::HeaderBar::new();
        header.set_show_title(false);
        let menu = gio::Menu::new();
        menu.append(Some("About Ditto"), Some("app.about"));
        menu.append(Some("Quit"), Some("app.quit"));
        let menu_button = gtk::MenuButton::builder()
            .icon_name("open-menu-symbolic")
            .menu_model(&menu)
            .tooltip_text("Menu")
            .build();
        menu_button.add_css_class("flat");
        header.pack_end(&menu_button);

        // Left column: identity and settings.
        let left = gtk::Box::new(gtk::Orientation::Vertical, 0);
        left.set_width_request(360);
        left.set_margin_start(40);
        left.set_margin_end(32);
        left.set_margin_top(4);
        left.set_margin_bottom(8);
        left.append(&label(&today_overline(), &["overline"]));
        let wordmark = widgets::wordmark();
        wordmark.set_margin_top(6);
        left.append(&wordmark);
        let lede = label("", &["lede"]);
        lede.set_wrap(true);
        lede.set_margin_top(8);
        lede.set_margin_bottom(28);
        left.append(&lede);

        let shortcut_cap = keycap("");
        let cap_button = gtk::Button::new();
        cap_button.set_child(Some(&shortcut_cap));
        cap_button.add_css_class("keycap-button");
        cap_button.set_tooltip_text(Some("Change shortcut"));
        cap_button.update_property(&[gtk::accessible::Property::Label("Change shortcut")]);
        {
            let ditto = ditto.clone();
            cap_button.connect_clicked(move |_| ditto.pick_shortcut());
        }
        left.append(&hairline(gtk::Orientation::Horizontal));
        left.append(&line("Shortcut", &cap_button));

        let voice = {
            let ditto = ditto.clone();
            dropdown(&Voice::ALL, Voice::label, config.voice, move |v| ditto.set_voice(v))
        };
        left.append(&line("Voice", &voice));

        let format = {
            let ditto = ditto.clone();
            dropdown(&Format::ALL, Format::label, config.format, move |f| ditto.set_format(f))
        };
        left.append(&line("Format", &format));

        let login = gtk::Switch::new();
        login.set_active(system::autostart_enabled());
        {
            let ditto = ditto.clone();
            login.connect_active_notify(move |s| ditto.set_autostart(s.is_active()));
        }
        left.append(&line("Start at login", &login));

        let spacer = gtk::Box::new(gtk::Orientation::Vertical, 0);
        spacer.set_vexpand(true);
        left.append(&spacer);
        let footnote = label(
            "Ditto keeps running after you close this window, so the shortcut always works.",
            &["footnote"],
        );
        footnote.set_wrap(true);
        left.append(&footnote);

        // Right column: recent notes.
        let right = gtk::Box::new(gtk::Orientation::Vertical, 0);
        right.set_hexpand(true);
        right.set_margin_start(32);
        right.set_margin_end(40);
        right.set_margin_top(4);
        right.set_margin_bottom(8);
        let head = gtk::Box::new(gtk::Orientation::Horizontal, 8);
        head.set_margin_bottom(12);
        let title = label("Recent", &["section-title"]);
        title.set_hexpand(true);
        let count = label("", &["count"]);
        head.append(&title);
        head.append(&count);
        right.append(&head);
        right.append(&hairline(gtk::Orientation::Horizontal));

        let list = gtk::ListBox::new();
        list.add_css_class("tracks");
        list.set_selection_mode(gtk::SelectionMode::None);
        {
            let ditto = ditto.clone();
            list.connect_row_activated(move |_, row| {
                if let Some(note) = ditto.notes().get(row.index() as usize) {
                    ditto.copy_note(&note.file);
                }
            });
        }
        let scroll = gtk::ScrolledWindow::builder()
            .hscrollbar_policy(gtk::PolicyType::Never)
            .vexpand(true)
            .child(&list)
            .build();
        let empty = label("No notes yet. Press the shortcut anywhere, or Record below.", &["empty-note"]);
        empty.set_wrap(true);
        empty.set_valign(gtk::Align::Start);
        empty.set_margin_top(16);
        let recent = gtk::Stack::new();
        recent.add_named(&scroll, Some("list"));
        recent.add_named(&empty, Some("empty"));
        right.append(&recent);

        let columns = gtk::Box::new(gtk::Orientation::Horizontal, 0);
        columns.set_vexpand(true);
        columns.append(&left);
        columns.append(&hairline(gtk::Orientation::Vertical));
        columns.append(&right);

        // Record dock across the bottom.
        let dock = gtk::Box::new(gtk::Orientation::Horizontal, 14);
        dock.add_css_class("dock");
        dock.set_margin_start(40);
        dock.set_margin_end(40);
        dock.set_margin_top(12);
        dock.set_margin_bottom(24);
        let timer = label("00:00", &["timer"]);
        let meter = widgets::level_meter(ditto.levels());
        let hint = label("", &["hint"]);
        let wave = widgets::waveform(ditto.levels());
        let middle = gtk::Stack::new();
        middle.set_hexpand(true);
        middle.set_transition_type(gtk::StackTransitionType::Crossfade);
        middle.add_named(&hint, Some("hint"));
        middle.add_named(&wave, Some("wave"));
        let discard = widgets::icon_button("window-close-symbolic", "Discard note");
        discard.set_valign(gtk::Align::Center);
        discard.set_visible(false);
        let record = widgets::pill_button("Record");
        record.set_tooltip_text(Some("Record (Ctrl+R)"));
        {
            let ditto = ditto.clone();
            record.connect_clicked(move |_| ditto.toggle());
        }
        {
            let ditto = ditto.clone();
            discard.connect_clicked(move |_| ditto.cancel());
        }
        dock.append(&timer);
        dock.append(&meter);
        dock.append(&middle);
        dock.append(&discard);
        dock.append(&record);

        let body = gtk::Box::new(gtk::Orientation::Vertical, 0);
        body.append(&header);
        body.append(&columns);
        body.append(&dock);
        let toasts = adw::ToastOverlay::new();
        toasts.set_child(Some(&body));
        window.set_content(Some(&toasts));

        let this = Rc::new(Self {
            window,
            toasts,
            lede,
            shortcut_cap,
            list,
            recent,
            count,
            dock,
            timer,
            meter,
            middle,
            hint,
            wave,
            record,
            discard,
        });
        this.set_shortcut(&ditto.shortcut_label(), ditto.shortcut_ok());
        this.set_idle();
        this
    }

    pub fn is_active(&self) -> bool {
        self.window.is_active()
    }

    pub fn toast(&self, text: &str) {
        let toast = adw::Toast::new(text);
        toast.set_timeout(3);
        self.toasts.add_toast(toast);
    }

    pub fn set_shortcut(&self, key: &str, working: bool) {
        self.shortcut_cap.set_label(key);
        if !working {
            self.lede.set_label("Couldn't register a GNOME shortcut. Bind any key to “ditto --toggle” in Settings › Keyboard.");
            self.hint.set_label("Record from here");
        } else if key == "Off" {
            self.lede.set_label(
                "The shortcut is off. Click the key next to Shortcut to pick one, or record from here.",
            );
            self.hint.set_label("Record from here");
        } else {
            self.lede.set_label(&format!(
                "{key} starts and stops a note anywhere. Then press Ctrl+V in your chat."
            ));
            self.hint.set_label(&format!("Press {key} anywhere"));
        }
    }

    pub fn set_idle(&self) {
        self.timer.set_label("00:00");
        self.timer.remove_css_class("live");
        self.dock.remove_css_class("live");
        self.middle.set_visible_child_name("hint");
        self.discard.set_visible(false);
        widgets::set_pill_label(&self.record, "Record");
        self.record.remove_css_class("stop");
        self.record.set_sensitive(true);
        self.redraw();
    }

    pub fn set_live(&self, elapsed: Duration) {
        self.timer.set_label(&clock(elapsed));
        self.timer.add_css_class("live");
        self.dock.add_css_class("live");
        self.middle.set_visible_child_name("wave");
        self.discard.set_visible(true);
        widgets::set_pill_label(&self.record, "Done");
        self.record.add_css_class("stop");
        self.redraw();
    }

    pub fn set_saving(&self) {
        self.discard.set_visible(false);
        widgets::set_pill_label(&self.record, "Saving…");
        self.record.set_sensitive(false);
    }

    pub fn redraw(&self) {
        self.meter.queue_draw();
        self.wave.queue_draw();
    }

    pub fn set_notes(&self, ditto: &Rc<Ditto>, notes: &[Note]) {
        self.list.remove_all();
        self.count.set_label(&format!("{:02}", notes.len()));
        self.recent.set_visible_child_name(if notes.is_empty() { "empty" } else { "list" });
        let playing = ditto.playing();
        for (index, note) in notes.iter().enumerate() {
            let is_playing = playing.as_deref() == Some(note.file.as_path());
            self.list.append(&track_row(ditto, index, note, is_playing));
        }
    }
}

fn track_row(ditto: &Rc<Ditto>, index: usize, note: &Note, playing: bool) -> gtk::ListBoxRow {
    let row_box = gtk::Box::new(gtk::Orientation::Horizontal, 16);
    row_box.set_margin_top(12);
    row_box.set_margin_bottom(12);
    row_box.set_margin_start(4);
    row_box.set_margin_end(4);

    let number = label(&format!("{:02}", index + 1), &["track-index"]);
    let text = gtk::Box::new(gtk::Orientation::Vertical, 2);
    text.set_hexpand(true);
    text.set_valign(gtk::Align::Center);
    text.append(&label("Voice note", &["track-title"]));
    text.append(&label(&track_subtitle(note), &["track-sub"]));
    let duration = label(&clock(Duration::from_millis(note.duration_ms)), &["track-duration"]);

    let actions = gtk::Box::new(gtk::Orientation::Horizontal, 2);
    actions.add_css_class("row-actions");
    let (icon, tip) = if playing {
        ("media-playback-stop-symbolic", "Stop")
    } else {
        ("media-playback-start-symbolic", "Play")
    };
    let play = widgets::icon_button(icon, tip);
    let copy = widgets::icon_button("edit-copy-symbolic", "Copy");
    let delete = widgets::icon_button("user-trash-symbolic", "Delete");
    for (button, action) in [(&play, 0u8), (&copy, 1), (&delete, 2)] {
        let ditto = ditto.clone();
        let file = note.file.clone();
        button.connect_clicked(move |_| match action {
            0 => ditto.play(&file),
            1 => ditto.copy_note(&file),
            _ => ditto.delete_note(&file),
        });
        actions.append(button);
    }

    row_box.append(&number);
    row_box.append(&text);
    row_box.append(&duration);
    row_box.append(&actions);

    let row = gtk::ListBoxRow::new();
    row.set_child(Some(&row_box));
    row.set_tooltip_text(Some("Click to copy"));
    row
}
