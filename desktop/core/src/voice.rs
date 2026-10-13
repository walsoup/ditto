use std::ffi::OsString;
use std::io;
use std::path::Path;
use std::process::{Command, Stdio};

use serde::{Deserialize, Serialize};

use crate::SAMPLE_RATE;

/// The voices shown as pills on Android, rendered with ffmpeg filters here.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum Voice {
    #[default]
    Raw,
    Studio,
    Titan,
    LoFi,
}

impl Voice {
    pub const ALL: [Voice; 4] = [Voice::Raw, Voice::Studio, Voice::Titan, Voice::LoFi];

    pub fn label(self) -> &'static str {
        match self {
            Voice::Raw => "Raw",
            Voice::Studio => "Studio",
            Voice::Titan => "Titan",
            Voice::LoFi => "Lo-Fi",
        }
    }

    pub fn filter(self) -> Option<&'static str> {
        match self {
            Voice::Raw => None,
            Voice::Studio => Some(
                "highpass=f=80,equalizer=f=120:t=q:w=1:g=3,equalizer=f=3500:t=q:w=1.2:g=4,\
                 acompressor=threshold=0.125:ratio=3:attack=10:release=120:makeup=2,alimiter=limit=0.95",
            ),
            // Same 0.72 resample as Android's Deep Titan: lower and slower.
            Voice::Titan => Some("asetrate=34560,aresample=48000,bass=g=4,alimiter=limit=0.95"),
            Voice::LoFi => {
                Some("highpass=f=350,lowpass=f=3200,acrusher=bits=10:mix=0.35,volume=1.6,alimiter=limit=0.9")
            }
        }
    }
}

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum Format {
    #[default]
    Ogg,
    M4a,
    Wav,
}

impl Format {
    pub const ALL: [Format; 3] = [Format::Ogg, Format::M4a, Format::Wav];

    pub fn label(self) -> &'static str {
        match self {
            Format::Ogg => "OGG",
            Format::M4a => "M4A",
            Format::Wav => "WAV",
        }
    }

    pub fn extension(self) -> &'static str {
        match self {
            Format::Ogg => "ogg",
            Format::M4a => "m4a",
            Format::Wav => "wav",
        }
    }

    fn codec_args(self) -> &'static [&'static str] {
        match self {
            Format::Ogg => &["-c:a", "libopus", "-b:a", "32k", "-application", "voip"],
            Format::M4a => &["-c:a", "aac", "-b:a", "96k", "-movflags", "+faststart"],
            Format::Wav => &["-c:a", "pcm_s16le"],
        }
    }
}

/// ffmpeg arguments that turn a raw s16le mono capture into a finished note.
pub fn encode_args(raw: &Path, out: &Path, voice: Voice, format: Format) -> Vec<OsString> {
    let rate = SAMPLE_RATE.to_string();
    let mut args: Vec<OsString> = ["-hide_banner", "-loglevel", "error", "-y", "-f", "s16le", "-ar"]
        .into_iter()
        .map(OsString::from)
        .collect();
    args.extend([rate.as_str(), "-ac", "1", "-i"].map(OsString::from));
    args.push(raw.into());
    if let Some(filter) = voice.filter() {
        args.push("-af".into());
        args.push(filter.into());
    }
    args.extend(format.codec_args().iter().map(OsString::from));
    args.push(out.into());
    args
}

pub fn encode(raw: &Path, out: &Path, voice: Voice, format: Format) -> io::Result<()> {
    if let Some(dir) = out.parent() {
        std::fs::create_dir_all(dir)?;
    }
    let output =
        Command::new("ffmpeg").args(encode_args(raw, out, voice, format)).stdin(Stdio::null()).output()?;
    if output.status.success() {
        Ok(())
    } else {
        Err(io::Error::other(String::from_utf8_lossy(&output.stderr).trim().to_string()))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn strings(args: &[OsString]) -> Vec<String> {
        args.iter().map(|a| a.to_string_lossy().into_owned()).collect()
    }

    #[test]
    fn raw_ogg_has_no_filter_and_uses_opus() {
        let args = strings(&encode_args(Path::new("in.pcm"), Path::new("out.ogg"), Voice::Raw, Format::Ogg));
        assert!(!args.contains(&"-af".to_string()));
        assert!(args.windows(2).any(|w| w == ["-c:a", "libopus"]));
        assert_eq!(args.last().unwrap(), "out.ogg");
        assert!(args.windows(2).any(|w| w == ["-i", "in.pcm"]));
    }

    #[test]
    fn filtered_voices_put_filter_after_input() {
        for voice in [Voice::Studio, Voice::Titan, Voice::LoFi] {
            let args = strings(&encode_args(Path::new("in.pcm"), Path::new("o.wav"), voice, Format::Wav));
            let input = args.iter().position(|a| a == "-i").unwrap();
            let af = args.iter().position(|a| a == "-af").expect("filter present");
            assert!(af > input);
            assert_eq!(args[af + 1], voice.filter().unwrap());
        }
    }

    #[test]
    fn extensions_match_formats() {
        let exts: Vec<_> = Format::ALL.iter().map(|f| f.extension()).collect();
        assert_eq!(exts, ["ogg", "m4a", "wav"]);
    }
}
