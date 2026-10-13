/// Where a single press of the shortcut leaves us. There is no key-release on
/// GNOME 46, so one key both starts and stops a note.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub enum Phase {
    #[default]
    Idle,
    Recording,
    Saving,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Step {
    Start,
    Stop,
    Ignore,
}

pub fn on_toggle(phase: Phase) -> Step {
    match phase {
        Phase::Idle => Step::Start,
        Phase::Recording => Step::Stop,
        Phase::Saving => Step::Ignore,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn press_starts_then_stops() {
        assert_eq!(on_toggle(Phase::Idle), Step::Start);
        assert_eq!(on_toggle(Phase::Recording), Step::Stop);
    }

    #[test]
    fn press_while_saving_is_ignored() {
        assert_eq!(on_toggle(Phase::Saving), Step::Ignore);
    }
}
