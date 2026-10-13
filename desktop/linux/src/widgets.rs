use std::cell::RefCell;
use std::collections::VecDeque;
use std::f64::consts::PI;
use std::rc::Rc;

use adw::prelude::*;
use gtk::cairo;

use crate::style::{palette, rgb};

/// Rolling input levels (0..1), newest last, shared by every live meter.
pub type Levels = Rc<RefCell<VecDeque<f32>>>;
pub const LEVEL_HISTORY: usize = 64;

pub fn label(text: &str, classes: &[&str]) -> gtk::Label {
    let label = gtk::Label::new(Some(text));
    label.set_xalign(0.0);
    for class in classes {
        label.add_css_class(class);
    }
    label
}

pub fn hairline(orientation: gtk::Orientation) -> gtk::Separator {
    let sep = gtk::Separator::new(orientation);
    sep.add_css_class("hairline");
    sep
}

pub fn wordmark() -> gtk::Box {
    let row = gtk::Box::new(gtk::Orientation::Horizontal, 0);
    row.append(&label("Ditto", &["wordmark"]));
    row.append(&label(".", &["wordmark", "wordmark-dot"]));
    row
}

pub fn keycap(text: &str) -> gtk::Label {
    let cap = gtk::Label::new(Some(text));
    cap.add_css_class("keycap");
    cap
}

/// A settings row: label on the left, control on the right, hairline below.
pub fn line(title: &str, trailing: &impl IsA<gtk::Widget>) -> gtk::Box {
    let column = gtk::Box::new(gtk::Orientation::Vertical, 0);
    let row = gtk::Box::new(gtk::Orientation::Horizontal, 12);
    row.add_css_class("line");
    let name = label(title, &["line-label"]);
    name.set_hexpand(true);
    row.append(&name);
    trailing.set_valign(gtk::Align::Center);
    row.append(trailing);
    column.append(&row);
    column.append(&hairline(gtk::Orientation::Horizontal));
    column
}

pub fn icon_button(icon: &str, tooltip: &str) -> gtk::Button {
    let button = gtk::Button::from_icon_name(icon);
    button.add_css_class("icon");
    button.set_tooltip_text(Some(tooltip));
    button.update_property(&[gtk::accessible::Property::Label(tooltip)]);
    button
}

/// 44px pill button that never wraps its label.
pub fn pill_button(text: &str) -> gtk::Button {
    let button = gtk::Button::new();
    let text = gtk::Label::new(Some(text));
    text.set_lines(1);
    text.set_single_line_mode(true);
    button.set_child(Some(&text));
    button.add_css_class("pill-button");
    button.set_valign(gtk::Align::Center);
    button
}

pub fn set_pill_label(button: &gtk::Button, text: &str) {
    if let Some(label) = button.child().and_downcast::<gtk::Label>() {
        label.set_label(text);
    }
}

fn rounded_rect(cr: &cairo::Context, x: f64, y: f64, w: f64, h: f64, r: f64) {
    let r = r.min(w / 2.0).min(h / 2.0);
    cr.new_sub_path();
    cr.arc(x + w - r, y + r, r, -PI / 2.0, 0.0);
    cr.arc(x + w - r, y + h - r, r, 0.0, PI / 2.0);
    cr.arc(x + r, y + h - r, r, PI / 2.0, PI);
    cr.arc(x + r, y + r, r, PI, 3.0 * PI / 2.0);
    cr.close_path();
}

fn source(cr: &cairo::Context, hex: u32) {
    let (r, g, b) = rgb(hex);
    cr.set_source_rgb(r, g, b);
}

/// Vertical capsule level meter, filled from the bottom.
pub fn level_meter(levels: &Levels) -> gtk::DrawingArea {
    let area = gtk::DrawingArea::new();
    area.set_content_width(6);
    area.set_content_height(28);
    area.set_valign(gtk::Align::Center);
    let levels = levels.clone();
    area.set_draw_func(move |_, cr, w, h| {
        let p = palette();
        let (w, h) = (w as f64, h as f64);
        source(cr, p.inactive);
        rounded_rect(cr, 0.0, 0.0, w, h, w / 2.0);
        let _ = cr.fill();
        let level = levels.borrow().back().copied().unwrap_or(0.0) as f64;
        if level > 0.0 {
            let fill = (h * level).max(w);
            source(cr, p.rec);
            rounded_rect(cr, 0.0, h - fill, w, fill, w / 2.0);
            let _ = cr.fill();
        }
    });
    area
}

/// Live waveform: 3px bars with 2px gaps, newest on the right.
pub fn waveform(levels: &Levels) -> gtk::DrawingArea {
    let area = gtk::DrawingArea::new();
    area.set_content_height(28);
    area.set_hexpand(true);
    area.set_valign(gtk::Align::Center);
    let levels = levels.clone();
    area.set_draw_func(move |_, cr, w, h| {
        let p = palette();
        let (w, h) = (w as f64, h as f64);
        let (bar, gap) = (3.0, 2.0);
        let slots = ((w + gap) / (bar + gap)).floor() as usize;
        let levels = levels.borrow();
        let shown: Vec<f32> = levels.iter().rev().take(slots).rev().copied().collect();
        let start = w - shown.len() as f64 * (bar + gap) + gap;
        source(cr, p.rec);
        for (i, level) in shown.iter().enumerate() {
            let bh = (h * *level as f64).max(3.0);
            let x = start + i as f64 * (bar + gap);
            rounded_rect(cr, x, (h - bh) / 2.0, bar, bh, bar / 2.0);
        }
        let _ = cr.fill();
    });
    area
}
