use crate::error::Result;
use std::path::Path;
use std::time::Duration;

#[derive(Clone, Debug)]
pub struct AudioBuffer {
    pub samples: Vec<f32>,
    pub sample_rate: u32,
    pub channels: u16,
}

impl AudioBuffer {
    pub fn new(sample_rate: u32, channels: u16) -> Self {
        Self {
            samples: Vec::new(),
            sample_rate,
            channels,
        }
    }

    pub fn duration(&self) -> Duration {
        let samples_per_channel = self.samples.len() / self.channels as usize;
        Duration::from_secs_f64(samples_per_channel as f64 / self.sample_rate as f64)
    }

    pub fn to_wav(&self, path: &Path) -> Result<()> {
        let spec = hound::WavSpec {
            channels: self.channels,
            sample_rate: self.sample_rate,
            bits_per_sample: 16,
            sample_format: hound::SampleFormat::Int,
        };

        let mut writer = hound::WavWriter::create(path, spec)?;

        for &sample in &self.samples {
            // Convert f32 [-1.0, 1.0] to i16
            let amplitude = (sample.clamp(-1.0, 1.0) * i16::MAX as f32) as i16;
            writer.write_sample(amplitude)?;
        }

        writer.finalize()?;
        tracing::info!("Wrote {} samples to {:?}", self.samples.len(), path);
        Ok(())
    }

    pub fn from_wav(path: &Path) -> Result<Self> {
        let mut reader = hound::WavReader::open(path)?;
        let spec = reader.spec();

        tracing::info!("Reading WAV: {:?} channels, {} Hz, {} bits",
            spec.channels, spec.sample_rate, spec.bits_per_sample);

        let samples: Vec<f32> = match spec.sample_format {
            hound::SampleFormat::Int => {
                reader
                    .samples::<i16>()
                    .map(|s| s.unwrap() as f32 / i16::MAX as f32)
                    .collect()
            }
            hound::SampleFormat::Float => {
                reader
                    .samples::<f32>()
                    .map(|s| s.unwrap())
                    .collect()
            }
        };

        Ok(Self {
            samples,
            sample_rate: spec.sample_rate,
            channels: spec.channels,
        })
    }

    pub fn file_size_mb(path: &Path) -> Result<f64> {
        let metadata = std::fs::metadata(path)?;
        Ok(metadata.len() as f64 / (1024.0 * 1024.0))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::path::PathBuf;

    #[test]
    fn test_duration_calculation() {
        let buffer = AudioBuffer {
            samples: vec![0.0; 16000],  // 1 second at 16kHz mono
            sample_rate: 16000,
            channels: 1,
        };

        let duration = buffer.duration();
        assert_eq!(duration.as_secs(), 1);
    }

    #[test]
    fn test_wav_roundtrip() {
        let original = AudioBuffer {
            samples: vec![0.0, 0.1, 0.2, 0.3, 0.4, 0.5],
            sample_rate: 16000,
            channels: 1,
        };

        let temp_path = PathBuf::from("/tmp/test_audio.wav");
        original.to_wav(&temp_path).unwrap();

        let loaded = AudioBuffer::from_wav(&temp_path).unwrap();

        assert_eq!(loaded.sample_rate, original.sample_rate);
        assert_eq!(loaded.channels, original.channels);
        assert_eq!(loaded.samples.len(), original.samples.len());

        std::fs::remove_file(temp_path).ok();
    }
}
