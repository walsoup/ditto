use std::cell::{Cell, RefCell};
use std::io;
use std::path::{Path, PathBuf};
use std::process::Child;
use std::rc::Rc;
use std::time::Duration;

use adw::prelude::*;
use gtk::{gio, glib};

use ditto_core::config::Config;
use ditto_core::history::{History, Note};
use ditto_core::toggle::{on_toggle, Phase, Step};
use ditto_core::voice::{encode, Format, Voice};
use ditto_core::{paths, APP_ID};

use crate::pill::Pill;
use crate::recorder::Recorder;
use crate::widgets::{Levels, LEVEL_HISTORY};
use crate::window::MainWindow;
use crate::{capture, clipboard, notify, style, system};

pub fn run() -> glib::ExitCode {
    let app = adw::Application::builder()
        .application_id(APP_ID)
        .flags(gio::ApplicationFlags::HANDLES_COMMAND_LINE)
        .build();

    let slot: Rc<RefCell<Option<Rc<Ditto>>>> = Rc::default();
    {
        let slot = slot.clone();
        app.connect_startup(move |app| {
            let ditto = Ditto::new(app);
            let weak = Rc::downgrade(&ditto);
            style::install(move || {
                if let Some(ditto) = weak.upgrade() {
                    ditto.redraw();
                }
            });
            ditto.install_actions();
            ditto.sync_shortcut();
            // Stay resident so the shortcut answers instantly and the copied
            // file stays on the clipboard after the window closes.
            std::mem::forget(app.hold());
            slot.replace(Some(ditto));
        });
    }
    app.connect_command_line(move |_, command| {
        let Some(ditto) = slot.borrow().clone() else { return glib::ExitCode::FAILURE };
        let args: Vec<String> =
            command.arguments().iter().map(|a| a.to_string_lossy().into_owned()).collect();
        if args.iter().any(|a| a == "--toggle") {
            ditto.toggle();
        } else if !args.iter().any(|a| a == "--background") {
            ditto.present();
        }
        glib::ExitCode::SUCCESS
    });
    app.run()
}

pub struct Ditto {
    app: adw::Application,
    config: RefCell<Config>,
    history: RefCell<History>,
    phase: Cell<Phase>,
    recorder: RefCell<Option<Recorder>>,
    levels: Levels,
    window: RefCell<Option<Rc<MainWindow>>>,
    pill: RefCell<Option<Pill>>,
    ticker: RefCell<Option<glib::SourceId>>,
    player: RefCell<Option<(PathBuf, Child)>>,
    notification: Rc<Cell<u32>>,
    shortcut_ok: Cell<bool>,
}

impl Ditto {
    fn new(app: &adw::Application) -> Rc<Self> {
        Rc::new(Self {
            app: app.clone(),
            config: RefCell::new(Config::load()),
            history: RefCell::new(History::load()),
            phase: Cell::default(),
            recorder: RefCell::default(),
            levels: Levels::default(),
            window: RefCell::default(),
            pill: RefCell::default(),
            ticker: RefCell::default(),
            player: RefCell::default(),
            notification: Rc::default(),
            shortcut_ok: Cell::new(true),
        })
    }

    pub fn app(&self) -> &adw::Application {
        &self.app
    }

    pub fn levels(&self) -> &Levels {
        &self.levels
    }

    pub fn config(&self) -> Config {
        self.config.borrow().clone()
    }

    pub fn notes(&self) -> Vec<Note> {
        self.history.borrow().notes().to_vec()
    }

    fn install_actions(self: &Rc<Self>) {
        let add = |name: &str, run: Box<dyn Fn()>| {
            let action = gio::SimpleAction::new(name, None);
            action.connect_activate(move |_, _| run());
            self.app.add_action(&action);
        };
        let ditto = self.clone();
        add("toggle", Box::new(move || ditto.toggle()));
        let ditto = self.clone();
        add("about", Box::new(move || ditto.about()));
        let app = self.app.clone();
        add("quit", Box::new(move || app.quit()));
        self.app.set_accels_for_action("app.toggle", &["<Control>r"]);
        self.app.set_accels_for_action("app.quit", &["<Control>q"]);
        self.app.set_accels_for_action("window.close", &["<Control>w"]);
    }

    fn main_window(self: &Rc<Self>) -> Rc<MainWindow> {
        let existing = self.window.borrow().clone();
        if let Some(window) = existing {
            return window;
        }
        let window = MainWindow::new(self);
        self.window.replace(Some(window.clone()));
        window.set_notes(self, &self.notes());
        window
    }

    pub fn present(self: &Rc<Self>) {
        self.main_window().window.present();
    }

    fn window_active(&self) -> bool {
        self.window.borrow().as_ref().is_some_and(|w| w.is_active())
    }

    fn redraw(&self) {
        if let Some(window) = self.window.borrow().as_ref() {
            window.redraw();
        }
    }

    // Recording

    pub fn toggle(self: &Rc<Self>) {
        match on_toggle(self.phase.get()) {
            Step::Start => self.start(),
            Step::Stop => self.stop(),
            Step::Ignore => {}
        }
    }

    fn start(self: &Rc<Self>) {
        self.stop_playback();
        let recorder = match Recorder::start(paths::cache_dir().join("take.pcm")) {
            Ok(recorder) => recorder,
            Err(_) => {
                self.announce("Microphone unavailable", "Check that a mic is connected and not muted.");
                return;
            }
        };
        self.recorder.replace(Some(recorder));
        self.levels.borrow_mut().clear();
        self.phase.set(Phase::Recording);
        if !self.window_active() {
            let pill = Pill::open(self);
            self.pill.replace(Some(pill));
        }
        self.show_live();

        let weak = Rc::downgrade(self);
        let ticker = glib::timeout_add_local(Duration::from_millis(50), move || match weak.upgrade() {
            Some(ditto) => ditto.tick(),
            None => glib::ControlFlow::Break,
        });
        self.ticker.replace(Some(ticker));
    }

    fn tick(self: &Rc<Self>) -> glib::ControlFlow {
        let (failed, level) = match self.recorder.borrow_mut().as_mut() {
            Some(recorder) => (recorder.has_failed(), recorder.level()),
            None => return glib::ControlFlow::Break,
        };
        if failed {
            // Returning Break ends this source, so forget it rather than remove it.
            drop(self.ticker.take());
            self.cancel();
            self.announce("Microphone unavailable", "Check that a mic is connected and not muted.");
            return glib::ControlFlow::Break;
        }
        {
            let mut levels = self.levels.borrow_mut();
            levels.push_back(level);
            while levels.len() > LEVEL_HISTORY {
                levels.pop_front();
            }
        }
        self.show_live();
        glib::ControlFlow::Continue
    }

    fn show_live(&self) {
        let elapsed = self.recorder.borrow().as_ref().map(Recorder::elapsed).unwrap_or_default();
        if let Some(window) = self.window.borrow().as_ref() {
            window.set_live(elapsed);
        }
        if let Some(pill) = self.pill.borrow().as_ref() {
            pill.update(elapsed);
        }
    }

    fn stop_ticker(&self) {
        if let Some(ticker) = self.ticker.take() {
            ticker.remove();
        }
    }

    fn stop(self: &Rc<Self>) {
        let Some(recorder) = self.recorder.take() else { return };
        self.stop_ticker();
        self.phase.set(Phase::Saving);
        if let Some(window) = self.window.borrow().as_ref() {
            window.set_saving();
        }
        if let Some(pill) = self.pill.borrow().as_ref() {
            pill.set_saving();
        }

        let Config { voice, format, .. } = self.config();
        let stamp = glib::DateTime::now_local()
            .and_then(|d| d.format("%Y-%m-%d-%H%M%S"))
            .map(|s| s.to_string())
            .unwrap_or_else(|_| "note".into());
        let out = paths::notes_dir().join(format!("ditto-{stamp}.{}", format.extension()));

        let ditto = self.clone();
        glib::spawn_future_local(async move {
            let job = gio::spawn_blocking(move || save(recorder, out, voice, format)).await;
            let result = job.unwrap_or_else(|_| Err(io::Error::other("Saving crashed")));
            ditto.saved(result);
        });
    }

    fn saved(self: &Rc<Self>, result: io::Result<Note>) {
        match result {
            Ok(note) => {
                let file = note.file.clone();
                let _ = self.history.borrow_mut().add(note);
                // Copy before the pill closes: Wayland only accepts the
                // clipboard from the window that has focus.
                self.copy(&file);
                self.end_recording();
                self.refresh_notes();
                self.announce("Copied", "Press Ctrl+V in your chat to send it.");
            }
            Err(error) => {
                self.end_recording();
                self.announce("Couldn't save the note", &error.to_string());
            }
        }
    }

    pub fn cancel(self: &Rc<Self>) {
        if self.phase.get() != Phase::Recording {
            return;
        }
        self.stop_ticker();
        let recorder = self.recorder.take();
        if let Some(recorder) = recorder {
            recorder.cancel();
        }
        self.end_recording();
    }

    fn end_recording(&self) {
        self.phase.set(Phase::Idle);
        self.levels.borrow_mut().clear();
        let pill = self.pill.take();
        if let Some(pill) = pill {
            pill.close();
        }
        if let Some(window) = self.window.borrow().as_ref() {
            window.set_idle();
        }
    }

    // Notes

    fn copy(&self, file: &Path) {
        let focused = self.window_active() || self.pill.borrow().as_ref().is_some_and(Pill::is_active);
        clipboard::copy_file(file, focused);
    }

    pub fn copy_note(&self, file: &Path) {
        self.copy(file);
        self.announce("Copied", "Press Ctrl+V in your chat to send it.");
    }

    pub fn delete_note(self: &Rc<Self>, file: &Path) {
        if self.playing().as_deref() == Some(file) {
            self.stop_playback();
        }
        if let Err(error) = self.history.borrow_mut().remove(file) {
            self.announce("Couldn't delete the note", &error.to_string());
        }
        self.refresh_notes();
    }

    pub fn playing(&self) -> Option<PathBuf> {
        self.player.borrow().as_ref().map(|(file, _)| file.clone())
    }

    pub fn play(self: &Rc<Self>, file: &Path) {
        let same = self.playing().as_deref() == Some(file);
        self.stop_playback();
        if !same {
            match system::play(file) {
                Ok(child) => {
                    self.player.replace(Some((file.to_path_buf(), child)));
                    self.watch_playback();
                }
                Err(_) => self.announce("Couldn't play the note", "ffplay is missing."),
            }
        }
        self.refresh_notes();
    }

    fn watch_playback(self: &Rc<Self>) {
        let weak = Rc::downgrade(self);
        glib::timeout_add_local(Duration::from_millis(300), move || {
            let Some(ditto) = weak.upgrade() else { return glib::ControlFlow::Break };
            let finished = match ditto.player.borrow_mut().as_mut() {
                Some((_, child)) => matches!(child.try_wait(), Ok(Some(_))),
                None => return glib::ControlFlow::Break,
            };
            if !finished {
                return glib::ControlFlow::Continue;
            }
            ditto.player.take();
            ditto.refresh_notes();
            glib::ControlFlow::Break
        });
    }

    fn stop_playback(&self) {
        let player = self.player.take();
        if let Some((_, mut child)) = player {
            let _ = child.kill();
            let _ = child.wait();
        }
    }

    fn refresh_notes(self: &Rc<Self>) {
        let window = self.window.borrow().clone();
        if let Some(window) = window {
            window.set_notes(self, &self.notes());
        }
    }

    // Settings

    pub fn set_voice(&self, voice: Voice) {
        self.config.borrow_mut().voice = voice;
        let _ = self.config.borrow().save();
    }

    pub fn set_format(&self, format: Format) {
        self.config.borrow_mut().format = format;
        let _ = self.config.borrow().save();
    }

    pub fn set_autostart(&self, enabled: bool) {
        if let Err(error) = system::set_autostart(enabled) {
            self.announce("Couldn't change Start at login", &error.to_string());
        }
    }

    fn sync_shortcut(&self) {
        let binding = self.config.borrow().shortcut.clone();
        self.shortcut_ok.set(system::install_shortcut(&binding).is_ok());
    }

    pub fn shortcut_ok(&self) -> bool {
        self.shortcut_ok.get()
    }

    pub fn shortcut_label(&self) -> String {
        let binding = self.config.borrow().shortcut.clone();
        if binding.is_empty() {
            return "Off".into();
        }
        gtk::accelerator_parse(&binding)
            .map(|(key, mods)| gtk::accelerator_get_label(key, mods).to_string())
            .unwrap_or(binding)
    }

    pub fn pick_shortcut(self: &Rc<Self>) {
        let _ = system::install_shortcut("");
        let parent = self.window.borrow().as_ref().map(|w| w.window.clone().upcast::<gtk::Window>());
        capture::open(self, parent.as_ref());
    }

    /// `None` keeps the current shortcut; `Some("")` turns it off.
    pub fn apply_shortcut(&self, chosen: Option<String>) {
        if let Some(binding) = chosen {
            self.config.borrow_mut().shortcut = binding;
            let _ = self.config.borrow().save();
        }
        self.sync_shortcut();
        if let Some(window) = self.window.borrow().as_ref() {
            window.set_shortcut(&self.shortcut_label(), self.shortcut_ok());
        }
    }

    fn about(&self) {
        let about = adw::AboutWindow::builder()
            .application_name("Ditto")
            .application_icon(APP_ID)
            .developer_name("walsoup")
            .version(env!("CARGO_PKG_VERSION"))
            .license_type(gtk::License::MitX11)
            .copyright("© 2026 walsoup")
            .comments("Press a key, say it, press it again, paste.")
            .modal(true)
            .build();
        if let Some(window) = self.window.borrow().as_ref() {
            about.set_transient_for(Some(&window.window));
        }
        about.present();
    }

    /// In-window toast when the user is looking at Ditto, a notification otherwise.
    fn announce(&self, summary: &str, body: &str) {
        if let Some(window) = self.window.borrow().as_ref().filter(|w| w.is_active()) {
            window.toast(&format!("{summary}. {body}"));
            return;
        }
        let id = self.notification.clone();
        notify::send(summary, body, id.get(), move |shown| id.set(shown));
    }
}

fn save(recorder: Recorder, out: PathBuf, voice: Voice, format: Format) -> io::Result<Note> {
    let take = recorder.finish()?;
    let encoded = encode(&take.raw, &out, voice, format);
    let _ = std::fs::remove_file(&take.raw);
    encoded?;
    Ok(Note::new(out, take.duration_ms, voice, format))
}
