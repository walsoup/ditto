use std::time::Duration;

use crate::SAMPLE_RATE;

/// Turns a stream of samples into one 0..1 level every 50 ms (the UI tick).
pub struct LevelMeter {
    window: usize,
    sum: f64,
    count: usize,
    carry: Option<u8>,
}

impl Default for LevelMeter {
    fn default() -> Self {
        Self { window: SAMPLE_RATE as usize / 20, sum: 0.0, count: 0, carry: None }
    }
}

impl LevelMeter {
    /// Feeds raw s16le bytes (any length, odd splits allowed) and returns the
    /// most recent completed level, if a window finished inside this chunk.
    pub fn feed(&mut self, bytes: &[u8]) -> Option<f32> {
        let mut latest = None;
        let mut i = 0;
        if let Some(lo) = self.carry.take() {
            match bytes.first() {
                Some(&hi) => {
                    latest = self.push(i16::from_le_bytes([lo, hi])).or(latest);
                    i = 1;
                }
                None => {
                    self.carry = Some(lo);
                    return None;
                }
            }
        }
        while i + 1 < bytes.len() {
            latest = self.push(i16::from_le_bytes([bytes[i], bytes[i + 1]])).or(latest);
            i += 2;
        }
        if i < bytes.len() {
            self.carry = Some(bytes[i]);
        }
        latest
    }

    fn push(&mut self, sample: i16) -> Option<f32> {
        let s = sample as f64;
        self.sum += s * s;
        self.count += 1;
        if self.count < self.window {
            return None;
        }
        let rms = (self.sum / self.count as f64).sqrt();
        self.sum = 0.0;
        self.count = 0;
        Some(level_from_rms(rms))
    }
}

/// Maps RMS to a perceptual 0..1 level over a 60 dB range.
pub fn level_from_rms(rms: f64) -> f32 {
    if rms <= 0.0 {
        return 0.0;
    }
    let db = 20.0 * (rms / i16::MAX as f64).log10();
    ((db + 60.0) / 60.0).clamp(0.0, 1.0) as f32
}

pub fn clock(elapsed: Duration) -> String {
    let s = elapsed.as_secs();
    format!("{:02}:{:02}", s / 60, s % 60)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn bytes_of(samples: &[i16]) -> Vec<u8> {
        samples.iter().flat_map(|s| s.to_le_bytes()).collect()
    }

    #[test]
    fn silence_is_zero_and_full_scale_is_one() {
        assert_eq!(level_from_rms(0.0), 0.0);
        assert!((level_from_rms(i16::MAX as f64) - 1.0).abs() < 1e-6);
    }

    #[test]
    fn emits_one_level_per_window_even_with_odd_splits() {
        let mut meter = LevelMeter::default();
        let data = bytes_of(&vec![8_000i16; 2_400]);
        let (a, b) = data.split_at(1_201);
        assert_eq!(meter.feed(a), None);
        let level = meter.feed(b).expect("window completes");
        assert!(level > 0.7 && level < 0.9, "level {level}");
    }

    #[test]
    fn clock_formats_minutes_and_seconds() {
        assert_eq!(clock(Duration::from_millis(0)), "00:00");
        assert_eq!(clock(Duration::from_secs(75)), "01:15");
    }
}
