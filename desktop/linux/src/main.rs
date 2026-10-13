mod app;
mod capture;
mod clipboard;
mod notify;
mod pill;
mod recorder;
mod style;
mod system;
mod widgets;
mod window;

fn main() -> gtk::glib::ExitCode {
    app::run()
}
