use std::fs::{self, File};
use std::io::{self, BufWriter, Read, Write};
use std::path::PathBuf;
use std::process::{Child, Command, Stdio};
use std::sync::atomic::{AtomicU32, AtomicU64, Ordering};
use std::sync::Arc;
use std::thread::{self, JoinHandle};
use std::time::{Duration, Instant};

use ditto_core::level::LevelMeter;
use ditto_core::{BYTES_PER_SECOND, SAMPLE_RATE};

pub struct Take {
    pub raw: PathBuf,
    pub duration_ms: u64,
}

/// Captures the default PipeWire source as raw s16le mono into `raw`.
pub struct Recorder {
    child: Child,
    reader: Option<JoinHandle<io::Result<u64>>>,
    level: Arc<AtomicU32>,
    raw: PathBuf,
    started: Instant,
}

impl Recorder {
    pub fn start(raw: PathBuf) -> io::Result<Self> {
        if let Some(dir) = raw.parent() {
            fs::create_dir_all(dir)?;
        }
        let mut file = BufWriter::new(File::create(&raw)?);
        let mut child = Command::new("pw-record")
            .args(["--rate", &SAMPLE_RATE.to_string(), "--channels", "1", "--format", "s16", "-"])
            .stdin(Stdio::null())
            .stdout(Stdio::piped())
            .stderr(Stdio::null())
            .spawn()?;
        let mut out = child.stdout.take().expect("stdout is piped");

        let level = Arc::new(AtomicU32::new(0));
        let shared = level.clone();
        let reader = thread::spawn(move || -> io::Result<u64> {
            let mut meter = LevelMeter::default();
            let mut buf = [0u8; 8192];
            let mut total = 0u64;
            loop {
                let n = match out.read(&mut buf) {
                    Ok(0) => break,
                    Ok(n) => n,
                    Err(e) if e.kind() == io::ErrorKind::Interrupted => continue,
                    Err(e) => return Err(e),
                };
                file.write_all(&buf[..n])?;
                total += n as u64;
                if let Some(l) = meter.feed(&buf[..n]) {
                    shared.store(l.to_bits(), Ordering::Relaxed);
                }
            }
            file.flush()?;
            Ok(total)
        });

        Ok(Self { child, reader: Some(reader), level, raw, started: Instant::now() })
    }

    pub fn level(&self) -> f32 {
        f32::from_bits(self.level.load(Ordering::Relaxed))
    }

    pub fn elapsed(&self) -> Duration {
        self.started.elapsed()
    }

    /// pw-record only exits on its own when the microphone can't be opened.
    pub fn has_failed(&mut self) -> bool {
        matches!(self.child.try_wait(), Ok(Some(_)))
    }

    pub fn finish(mut self) -> io::Result<Take> {
        let _ = self.child.kill();
        let _ = self.child.wait();
        let bytes = match self.reader.take().map(JoinHandle::join) {
            Some(Ok(result)) => result?,
            Some(Err(_)) => return Err(io::Error::other("recorder thread panicked")),
            None => 0,
        };
        if bytes == 0 {
            return Err(io::Error::other("Microphone unavailable"));
        }
        Ok(Take { raw: self.raw.clone(), duration_ms: bytes * 1000 / BYTES_PER_SECOND })
    }

    pub fn cancel(self) {
        let raw = self.raw.clone();
        let _ = self.finish();
        let _ = fs::remove_file(raw);
    }
}

impl Drop for Recorder {
    fn drop(&mut self) {
        if self.reader.is_some() {
            let _ = self.child.kill();
            let _ = self.child.wait();
        }
    }
}
