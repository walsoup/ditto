//! Text formats for GNOME custom keybindings, as read and written by `gsettings`.

pub const KEYBINDINGS_SCHEMA: &str = "org.gnome.settings-daemon.plugins.media-keys";
pub const ENTRY_SCHEMA: &str = "org.gnome.settings-daemon.plugins.media-keys.custom-keybinding";
pub const ENTRY_PATH: &str = "/org/gnome/settings-daemon/plugins/media-keys/custom-keybindings/ditto/";

/// Parses a GVariant string array such as `@as []` or `['/a/', '/b/']`.
pub fn parse_string_list(text: &str) -> Vec<String> {
    let text = text.trim().trim_start_matches("@as").trim();
    let inner = text.trim_start_matches('[').trim_end_matches(']');
    inner
        .split(',')
        .map(|item| item.trim().trim_matches('\'').trim_matches('"').to_string())
        .filter(|item| !item.is_empty())
        .collect()
}

pub fn format_string_list(items: &[String]) -> String {
    let quoted: Vec<String> = items.iter().map(|i| quote(i)).collect();
    format!("[{}]", quoted.join(", "))
}

/// A GVariant string literal.
pub fn quote(value: &str) -> String {
    format!("'{}'", value.replace('\\', "\\\\").replace('\'', "\\'"))
}

/// Shell-safe argv[0] for GNOME's custom command, which is split with g_shell_parse_argv.
pub fn command_for(exe: &str, arg: &str) -> String {
    if exe.chars().all(|c| c.is_ascii_alphanumeric() || "/._-+".contains(c)) {
        format!("{exe} {arg}")
    } else {
        format!("'{}' {arg}", exe.replace('\'', r"'\''"))
    }
}

/// Adds our entry to the custom keybinding list, keeping everyone else's.
pub fn with_entry(mut list: Vec<String>) -> Vec<String> {
    if !list.iter().any(|p| p == ENTRY_PATH) {
        list.push(ENTRY_PATH.to_string());
    }
    list
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_empty_and_populated_lists() {
        assert!(parse_string_list("@as []").is_empty());
        assert_eq!(parse_string_list("['/x/custom0/', '/x/custom1/']\n"), ["/x/custom0/", "/x/custom1/"]);
    }

    #[test]
    fn appends_entry_once_and_round_trips() {
        let list = with_entry(parse_string_list("['/x/custom0/']"));
        assert_eq!(list.len(), 2);
        assert_eq!(with_entry(list.clone()), list);
        assert_eq!(parse_string_list(&format_string_list(&list)), list);
    }

    #[test]
    fn quotes_paths_with_spaces() {
        assert_eq!(command_for("/home/w/.local/bin/ditto", "--toggle"), "/home/w/.local/bin/ditto --toggle");
        assert_eq!(command_for("/home/w/my apps/ditto", "--toggle"), "'/home/w/my apps/ditto' --toggle");
        assert_eq!(quote("it's"), r"'it\'s'");
    }
}
