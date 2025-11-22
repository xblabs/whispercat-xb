use crate::audio::buffer::AudioBuffer;
use crate::config::AudioConfig;
use crate::error::{Result, WhisperCatError};
use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use std::time::Instant;

pub struct AudioRecorder {
    state: Arc<Mutex<RecordingState>>,
    config: AudioConfig,
    output_dir: PathBuf,
}

pub enum RecordingState {
    Idle,
    Recording {
        start_time: Instant,
        samples: Arc<Mutex<Vec<f32>>>,
        _stream: cpal::Stream,
    },
    Processing,
}

impl std::fmt::Debug for RecordingState {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Idle => write!(f, "Idle"),
            Self::Recording { start_time, .. } => {
                write!(f, "Recording {{ start_time: {:?}, ... }}", start_time)
            }
            Self::Processing => write!(f, "Processing"),
        }
    }
}

impl AudioRecorder {
    pub fn new(config: AudioConfig, output_dir: PathBuf) -> Result<Self> {
        std::fs::create_dir_all(&output_dir)?;

        Ok(Self {
            state: Arc::new(Mutex::new(RecordingState::Idle)),
            config,
            output_dir,
        })
    }

    pub fn list_devices() -> Result<Vec<String>> {
        let host = cpal::default_host();
        let devices: Result<Vec<String>> = host
            .input_devices()?
            .map(|d| d.name().map_err(|e| WhisperCatError::AudioDeviceError(e.to_string())))
            .collect();
        devices
    }

    pub fn start_recording(&mut self) -> Result<()> {
        let mut state = self.state.lock().unwrap();

        if matches!(*state, RecordingState::Recording { .. }) {
            return Err(WhisperCatError::RecordingError(
                "Already recording".into(),
            ));
        }

        // Get audio device
        let host = cpal::default_host();
        let device = if let Some(name) = &self.config.device_name {
            host.input_devices()?
                .find(|d| d.name().ok().as_ref() == Some(name))
                .ok_or_else(|| {
                    WhisperCatError::RecordingError(format!("Device '{}' not found", name))
                })?
        } else {
            host.default_input_device().ok_or_else(|| {
                WhisperCatError::RecordingError("No input device available".into())
            })?
        };

        tracing::info!("Using audio device: {}", device.name().unwrap_or_default());

        // Configure stream
        let config = device.default_input_config()?;
        tracing::info!("Recording config: {:?}", config);

        // Shared buffer for recorded samples
        let samples = Arc::new(Mutex::new(Vec::new()));
        let samples_clone = samples.clone();

        // Build input stream
        let stream = device.build_input_stream(
            &config.into(),
            move |data: &[f32], _: &cpal::InputCallbackInfo| {
                // Called on audio thread - copy samples to buffer
                samples_clone.lock().unwrap().extend_from_slice(data);
            },
            |err| {
                tracing::error!("Stream error: {}", err);
            },
            None,
        )?;

        stream.play()?;

        *state = RecordingState::Recording {
            start_time: Instant::now(),
            samples,
            _stream: stream,
        };

        tracing::info!("Recording started");
        Ok(())
    }

    pub fn stop_recording(&mut self) -> Result<PathBuf> {
        let mut state = self.state.lock().unwrap();

        let (samples, start_time) = match std::mem::replace(&mut *state, RecordingState::Processing) {
            RecordingState::Recording { samples, start_time, .. } => (samples, start_time),
            _ => {
                return Err(WhisperCatError::RecordingError("Not recording".into()))
            }
        };

        drop(state); // Release lock before processing

        // Extract samples
        let samples_vec = Arc::try_unwrap(samples)
            .map_err(|_| WhisperCatError::RecordingError("Failed to extract samples".into()))?
            .into_inner()
            .unwrap();

        // Generate filename
        let filename = format!(
            "recording_{}.wav",
            chrono::Utc::now().format("%Y%m%d_%H%M%S")
        );
        let output_path = self.output_dir.join(filename);

        // Save to WAV
        let buffer = AudioBuffer {
            samples: samples_vec,
            sample_rate: self.config.sample_rate,
            channels: self.config.channels,
        };

        buffer.to_wav(&output_path)?;

        let duration = start_time.elapsed();
        tracing::info!(
            "Recording stopped: {:?}, saved to {:?}",
            duration,
            output_path
        );

        *self.state.lock().unwrap() = RecordingState::Idle;

        Ok(output_path)
    }

    pub fn is_recording(&self) -> bool {
        matches!(*self.state.lock().unwrap(), RecordingState::Recording { .. })
    }

    pub fn duration(&self) -> Option<std::time::Duration> {
        match &*self.state.lock().unwrap() {
            RecordingState::Recording { start_time, .. } => Some(start_time.elapsed()),
            _ => None,
        }
    }
}
