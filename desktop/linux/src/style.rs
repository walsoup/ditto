use adw::prelude::*;
use gtk::gdk;

/// Same tokens as the Android `DittoColors`.
#[derive(Clone, Copy)]
pub struct Palette {
    pub bg: u32,
    pub surface: u32,
    pub container: u32,
    pub container_low: u32,
    pub hairline: u32,
    pub text: u32,
    pub text_secondary: u32,
    pub muted: u32,
    pub primary: u32,
    pub on_primary: u32,
    pub rec: u32,
    pub rec_soft: u32,
    pub inactive: u32,
}

pub const LIGHT: Palette = Palette {
    bg: 0xFAF9F6,
    surface: 0xFFFFFF,
    container: 0xE8F1EC,
    container_low: 0xF1F5F2,
    hairline: 0xE0E6E2,
    text: 0x1F2421,
    text_secondary: 0x526058,
    muted: 0x8A9A90,
    primary: 0x2D6A4F,
    on_primary: 0xFFFFFF,
    rec: 0xD47255,
    rec_soft: 0xFBF0EC,
    inactive: 0xD0D7D3,
};

pub const DARK: Palette = Palette {
    bg: 0x121513,
    surface: 0x1A1E1B,
    container: 0x22302A,
    container_low: 0x1D2520,
    hairline: 0x2A322D,
    text: 0xE8ECE9,
    text_secondary: 0xA3B0A8,
    muted: 0x6F7D75,
    primary: 0x7DBB9B,
    on_primary: 0x0F1A14,
    rec: 0xE08A6E,
    rec_soft: 0x33241F,
    inactive: 0x3A433E,
};

pub fn palette() -> Palette {
    if adw::StyleManager::default().is_dark() {
        DARK
    } else {
        LIGHT
    }
}

pub fn rgb(hex: u32) -> (f64, f64, f64) {
    let c = |shift: u32| ((hex >> shift) & 0xFF) as f64 / 255.0;
    (c(16), c(8), c(0))
}

fn tokens(p: &Palette) -> String {
    let defs = [
        ("d_bg", p.bg),
        ("d_surface", p.surface),
        ("d_container", p.container),
        ("d_container_low", p.container_low),
        ("d_hairline", p.hairline),
        ("d_text", p.text),
        ("d_text2", p.text_secondary),
        ("d_muted", p.muted),
        ("d_primary", p.primary),
        ("d_on_primary", p.on_primary),
        ("d_rec", p.rec),
        ("d_rec_soft", p.rec_soft),
        ("d_inactive", p.inactive),
    ];
    let mut css: String =
        defs.iter().map(|(name, hex)| format!("@define-color {name} #{hex:06X};\n")).collect();
    css.push_str(ADWAITA_ALIASES);
    css
}

const ADWAITA_ALIASES: &str = "
@define-color window_bg_color @d_bg;
@define-color window_fg_color @d_text;
@define-color view_bg_color @d_bg;
@define-color view_fg_color @d_text;
@define-color headerbar_bg_color @d_bg;
@define-color headerbar_fg_color @d_text;
@define-color headerbar_border_color @d_hairline;
@define-color headerbar_shade_color transparent;
@define-color popover_bg_color @d_surface;
@define-color popover_fg_color @d_text;
@define-color dialog_bg_color @d_bg;
@define-color dialog_fg_color @d_text;
@define-color card_bg_color @d_surface;
@define-color accent_bg_color @d_primary;
@define-color accent_fg_color @d_on_primary;
@define-color accent_color @d_primary;
@define-color destructive_bg_color @d_rec;
@define-color destructive_color @d_rec;
";

/// Installs the stylesheet and keeps the token layer in step with the system
/// light/dark preference. Calls `on_change` after each switch so custom-drawn
/// widgets can repaint.
pub fn install(on_change: impl Fn() + 'static) {
    let Some(display) = gdk::Display::default() else { return };

    let rules = gtk::CssProvider::new();
    rules.load_from_string(include_str!("style.css"));
    gtk::style_context_add_provider_for_display(&display, &rules, gtk::STYLE_PROVIDER_PRIORITY_APPLICATION);

    let colors = gtk::CssProvider::new();
    gtk::style_context_add_provider_for_display(
        &display,
        &colors,
        gtk::STYLE_PROVIDER_PRIORITY_APPLICATION + 1,
    );

    let manager = adw::StyleManager::default();
    let apply = move |manager: &adw::StyleManager| {
        colors.load_from_string(&tokens(if manager.is_dark() { &DARK } else { &LIGHT }));
    };
    apply(&manager);
    manager.connect_dark_notify(move |manager| {
        apply(manager);
        on_change();
    });
}
