# WhisperCat Rust - Granular Implementation Guide

**Version:** 0.1.0
**Date:** 2025-11-22
**Companion to:** SPEC.md

This document provides granular implementation details for each module.

---

## Table of Contents

1. [Data Structures](#data-structures)
2. [Audio Recording Implementation](#audio-recording-implementation)
3. [Silence Removal Algorithm](#silence-removal-algorithm)
4. [Transcription Client Details](#transcription-client-details)
5. [Pipeline Execution](#pipeline-execution)
6. [UI State Management](#ui-state-management)
7. [Configuration Persistence](#configuration-persistence)

---

## Data Structures

### Audio Module

```rust
// src/audio/buffer.rs
#[derive(Clone, Debug)]
pub struct AudioBuffer {
    pub samples: Vec<f32>,      // Normalized to [-1.0, 1.0]
    pub sample_rate: u32,        // e.g., 16000 Hz
    pub channels: u16,           // 1 for mono, 2 for stereo
}

impl AudioBuffer {
    pub fn duration(&self) -> Duration {
        let samples_per_channel = self.samples.len() / self.channels as usize;
        Duration::from_secs_f64(samples_per_channel as f64 / self.sample_rate as f64)
    }

    pub fn to_wav(&self, path: &Path) -> Result<()> {
        // Convert f32 samples to i16 for WAV format
        let spec = hound::WavSpec {
            channels: self.channels,
            sample_rate: self.sample_rate,
            bits_per_sample: 16,
            sample_format: hound::SampleFormat::Int,
        };

        let mut writer = hound::WavWriter::create(path, spec)?;
        for &sample in &self.samples {
            let amplitude = (sample * i16::MAX as f32) as i16;
            writer.write_sample(amplitude)?;
        }
        writer.finalize()?;
        Ok(())
    }

    pub fn from_wav(path: &Path) -> Result<Self> {
        let mut reader = hound::WavReader::open(path)?;
        let spec = reader.spec();

        let samples: Vec<f32> = reader
            .samples::<i16>()
            .map(|s| s.unwrap() as f32 / i16::MAX as f32)
            .collect();

        Ok(Self {
            samples,
            sample_rate: spec.sample_rate,
            channels: spec.channels,
        })
    }
}

// src/audio/recorder.rs
pub struct AudioRecorder {
    state: Arc<Mutex<RecordingState>>,
    config: AudioConfig,
    output_dir: PathBuf,
}

#[derive(Debug)]
pub enum RecordingState {
    Idle,
    Recording {
        start_time: Instant,
        samples: Vec<f32>,
        _stream: cpal::Stream,  // Keep alive
    },
    Processing,
    Error(String),
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct AudioConfig {
    pub sample_rate: u32,
    pub channels: u16,
    pub device_name: Option<String>,
}

impl Default for AudioConfig {
    fn default() -> Self {
        Self {
            sample_rate: 16000,  // Optimal for speech
            channels: 1,          // Mono
            device_name: None,    // Use default device
        }
    }
}
```

### Transcription Module

```rust
// src/transcription/types.rs
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TranscriptionRequest {
    #[serde(skip)]
    pub file_path: PathBuf,

    pub model: String,           // "whisper-1"

    #[serde(skip_serializing_if = "Option::is_none")]
    pub language: Option<String>,  // "en", "es", etc.

    #[serde(skip_serializing_if = "Option::is_none")]
    pub prompt: Option<String>,    // Context hint

    #[serde(skip_serializing_if = "Option::is_none")]
    pub temperature: Option<f32>,  // 0.0 - 1.0

    #[serde(skip_serializing_if = "Option::is_none")]
    pub response_format: Option<String>,  // "json", "text", "verbose_json"
}

#[derive(Debug, Clone, Deserialize)]
pub struct TranscriptionResponse {
    pub text: String,

    #[serde(default)]
    pub duration: Option<f64>,

    #[serde(default)]
    pub language: Option<String>,
}

// src/transcription/whisper.rs
#[derive(Clone)]
pub struct WhisperClient {
    api_key: String,
    client: reqwest::Client,
    base_url: String,
}

impl WhisperClient {
    pub fn new(api_key: String) -> Self {
        Self {
            api_key,
            client: reqwest::Client::new(),
            base_url: "https://api.openai.com/v1".to_string(),
        }
    }

    pub async fn transcribe(&self, request: TranscriptionRequest) -> Result<TranscriptionResponse> {
        // Pre-flight checks
        self.validate_file_size(&request.file_path)?;

        // Build multipart form
        let form = self.build_multipart_form(&request).await?;

        // Make API request
        let response = self.client
            .post(format!("{}/audio/transcriptions", self.base_url))
            .header("Authorization", format!("Bearer {}", self.api_key))
            .multipart(form)
            .send()
            .await?;

        // Handle response
        if !response.status().is_success() {
            let status = response.status();
            let body = response.text().await?;
            return Err(WhisperCatError::ApiError {
                status: status.as_u16(),
                message: body,
            });
        }

        let result: TranscriptionResponse = response.json().await?;
        Ok(result)
    }

    fn validate_file_size(&self, path: &Path) -> Result<()> {
        let metadata = std::fs::metadata(path)?;
        let size_mb = metadata.len() as f64 / (1024.0 * 1024.0);

        if size_mb > 25.0 {
            return Err(WhisperCatError::FileSizeError { size: size_mb });
        }
        Ok(())
    }

    async fn build_multipart_form(&self, request: &TranscriptionRequest) -> Result<reqwest::multipart::Form> {
        let file_bytes = tokio::fs::read(&request.file_path).await?;
        let file_name = request.file_path
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or("audio.wav")
            .to_string();

        let file_part = reqwest::multipart::Part::bytes(file_bytes)
            .file_name(file_name)
            .mime_str("audio/wav")?;

        let mut form = reqwest::multipart::Form::new()
            .part("file", file_part)
            .text("model", request.model.clone());

        if let Some(lang) = &request.language {
            form = form.text("language", lang.clone());
        }
        if let Some(prompt) = &request.prompt {
            form = form.text("prompt", prompt.clone());
        }
        if let Some(temp) = request.temperature {
            form = form.text("temperature", temp.to_string());
        }

        Ok(form)
    }
}
```

### Pipeline Module

```rust
// src/pipeline/unit.rs
#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(tag = "type")]
pub enum Unit {
    Prompt {
        id: Uuid,
        name: String,
        provider: Provider,
        model: String,
        system_prompt: String,
        user_prompt_template: String,  // Can contain {{input}} placeholder
    },
    TextReplacement {
        id: Uuid,
        name: String,
        find: String,
        replace: String,
        regex: bool,
        case_sensitive: bool,
    },
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub enum Provider {
    OpenAI,
    // Future: Anthropic, LocalLLM, etc.
}

// src/pipeline/pipeline.rs
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Pipeline {
    pub id: Uuid,
    pub name: String,
    pub description: Option<String>,
    pub units: Vec<Unit>,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

impl Pipeline {
    pub fn new(name: String) -> Self {
        let now = Utc::now();
        Self {
            id: Uuid::new_v4(),
            name,
            description: None,
            units: Vec::new(),
            created_at: now,
            updated_at: now,
        }
    }

    pub fn add_unit(&mut self, unit: Unit) {
        self.units.push(unit);
        self.updated_at = Utc::now();
    }
}

// src/pipeline/executor.rs
pub struct PipelineExecutor {
    openai_client: OpenAIClient,
    logger: Arc<Mutex<Vec<ExecutionLogEntry>>>,
}

#[derive(Clone, Debug)]
pub struct ExecutionLogEntry {
    pub timestamp: DateTime<Utc>,
    pub unit_name: String,
    pub input: String,
    pub output: String,
    pub duration: Duration,
}

impl PipelineExecutor {
    pub async fn execute(&self, input: String, pipeline: &Pipeline) -> Result<String> {
        let mut current_text = input;

        for (idx, unit) in pipeline.units.iter().enumerate() {
            let start = Instant::now();
            tracing::info!("Executing unit {}/{}: {}", idx + 1, pipeline.units.len(), unit.name());

            let output = self.execute_unit(&current_text, unit).await?;
            let duration = start.elapsed();

            // Log execution
            self.logger.lock().unwrap().push(ExecutionLogEntry {
                timestamp: Utc::now(),
                unit_name: unit.name().to_string(),
                input: current_text.clone(),
                output: output.clone(),
                duration,
            });

            current_text = output;
        }

        Ok(current_text)
    }

    async fn execute_unit(&self, input: &str, unit: &Unit) -> Result<String> {
        match unit {
            Unit::Prompt { system_prompt, user_prompt_template, model, .. } => {
                let user_prompt = user_prompt_template.replace("{{input}}", input);
                self.openai_client.chat_completion(system_prompt, &user_prompt, model).await
            }
            Unit::TextReplacement { find, replace, regex, case_sensitive, .. } => {
                if *regex {
                    let re = if *case_sensitive {
                        regex::Regex::new(find)?
                    } else {
                        regex::RegexBuilder::new(find).case_insensitive(true).build()?
                    };
                    Ok(re.replace_all(input, replace.as_str()).to_string())
                } else {
                    Ok(if *case_sensitive {
                        input.replace(find, replace)
                    } else {
                        // Case-insensitive string replacement
                        let re = regex::RegexBuilder::new(&regex::escape(find))
                            .case_insensitive(true)
                            .build()?;
                        re.replace_all(input, replace.as_str()).to_string()
                    })
                }
            }
        }
    }
}

impl Unit {
    pub fn name(&self) -> &str {
        match self {
            Unit::Prompt { name, .. } => name,
            Unit::TextReplacement { name, .. } => name,
        }
    }
}
```

---

## Audio Recording Implementation

### Step-by-Step Implementation

```rust
// src/audio/recorder.rs

use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};

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
            .map(|d| d.name().map_err(Into::into))
            .collect();
        devices
    }

    pub fn start_recording(&mut self) -> Result<()> {
        let mut state = self.state.lock().unwrap();

        if matches!(*state, RecordingState::Recording { .. }) {
            return Err(WhisperCatError::RecordingError("Already recording".into()));
        }

        // Get audio device
        let host = cpal::default_host();
        let device = if let Some(name) = &self.config.device_name {
            host.input_devices()?
                .find(|d| d.name().ok().as_ref() == Some(name))
                .ok_or_else(|| WhisperCatError::RecordingError(format!("Device '{}' not found", name)))?
        } else {
            host.default_input_device()
                .ok_or_else(|| WhisperCatError::RecordingError("No input device available".into()))?
        };

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
            samples: Arc::try_unwrap(samples).unwrap().into_inner().unwrap(),
            _stream: stream,
        };

        tracing::info!("Recording started");
        Ok(())
    }

    pub fn stop_recording(&mut self) -> Result<PathBuf> {
        let mut state = self.state.lock().unwrap();

        let (samples, start_time) = match std::mem::replace(&mut *state, RecordingState::Processing) {
            RecordingState::Recording { samples, start_time, .. } => (samples, start_time),
            _ => return Err(WhisperCatError::RecordingError("Not recording".into())),
        };

        drop(state); // Release lock before processing

        // Generate filename
        let filename = format!("recording_{}.wav", chrono::Utc::now().format("%Y%m%d_%H%M%S"));
        let output_path = self.output_dir.join(filename);

        // Save to WAV
        let buffer = AudioBuffer {
            samples,
            sample_rate: self.config.sample_rate,
            channels: self.config.channels,
        };

        buffer.to_wav(&output_path)?;

        let duration = start_time.elapsed();
        tracing::info!("Recording stopped: {:?}, saved to {:?}", duration, output_path);

        *self.state.lock().unwrap() = RecordingState::Idle;

        Ok(output_path)
    }

    pub fn is_recording(&self) -> bool {
        matches!(*self.state.lock().unwrap(), RecordingState::Recording { .. })
    }

    pub fn duration(&self) -> Option<Duration> {
        match &*self.state.lock().unwrap() {
            RecordingState::Recording { start_time, .. } => Some(start_time.elapsed()),
            _ => None,
        }
    }
}
```

---

## Silence Removal Algorithm

### Detailed Implementation

```rust
// src/audio/processor.rs

pub struct SilenceRemover {
    threshold: f32,         // RMS threshold (e.g., 0.01)
    min_duration_ms: u32,   // Minimum silence duration to remove (e.g., 1500ms)
    window_size_ms: u32,    // Analysis window size (100ms)
}

impl SilenceRemover {
    pub fn new(threshold: f32, min_duration_ms: u32) -> Self {
        Self {
            threshold,
            min_duration_ms,
            window_size_ms: 100,  // Fixed at 100ms per Java implementation
        }
    }

    pub fn remove_silence(&self, audio: &AudioBuffer) -> Result<(AudioBuffer, SilenceAnalysis)> {
        // Step 1: Calculate RMS for each window
        let window_size_samples = (audio.sample_rate as f64 * self.window_size_ms as f64 / 1000.0) as usize;
        let windows = audio.samples.chunks(window_size_samples);

        let mut rms_values = Vec::new();
        let mut min_rms = f32::MAX;
        let mut max_rms = f32::MIN;
        let mut sum_rms = 0.0f32;

        for window in windows {
            let rms = calculate_rms(window);
            rms_values.push(rms);
            min_rms = min_rms.min(rms);
            max_rms = max_rms.max(rms);
            sum_rms += rms;
        }

        let avg_rms = sum_rms / rms_values.len() as f32;

        // Step 2: Mark silence regions
        let min_silence_windows = (self.min_duration_ms / self.window_size_ms) as usize;
        let mut silence_regions = Vec::new();
        let mut silence_start: Option<usize> = None;

        for (i, &rms) in rms_values.iter().enumerate() {
            if rms < self.threshold {
                // Start or continue silence region
                if silence_start.is_none() {
                    silence_start = Some(i);
                }
            } else {
                // End silence region
                if let Some(start) = silence_start {
                    let duration_windows = i - start;
                    if duration_windows >= min_silence_windows {
                        silence_regions.push(SilenceRegion {
                            start_frame: start * window_size_samples,
                            end_frame: i * window_size_samples,
                        });
                    }
                    silence_start = None;
                }
            }
        }

        // Handle trailing silence
        if let Some(start) = silence_start {
            let duration_windows = rms_values.len() - start;
            if duration_windows >= min_silence_windows {
                silence_regions.push(SilenceRegion {
                    start_frame: start * window_size_samples,
                    end_frame: audio.samples.len(),
                });
            }
        }

        // Step 3: Build new audio without silence regions
        let mut new_samples = Vec::new();
        let mut last_end = 0;

        for region in &silence_regions {
            // Copy audio before this silence region
            new_samples.extend_from_slice(&audio.samples[last_end..region.start_frame]);
            last_end = region.end_frame;
        }

        // Copy remaining audio after last silence region
        new_samples.extend_from_slice(&audio.samples[last_end..]);

        let original_duration = audio.duration();
        let new_buffer = AudioBuffer {
            samples: new_samples,
            sample_rate: audio.sample_rate,
            channels: audio.channels,
        };
        let new_duration = new_buffer.duration();

        let reduction_percent = if original_duration.as_secs_f64() > 0.0 {
            ((original_duration.as_secs_f64() - new_duration.as_secs_f64()) / original_duration.as_secs_f64() * 100.0) as f32
        } else {
            0.0
        };

        let analysis = SilenceAnalysis {
            min_rms,
            max_rms,
            avg_rms,
            silence_regions,
            reduction_percent,
            original_duration,
            new_duration,
        };

        Ok((new_buffer, analysis))
    }
}

fn calculate_rms(samples: &[f32]) -> f32 {
    if samples.is_empty() {
        return 0.0;
    }

    let sum_squares: f32 = samples.iter().map(|&s| s * s).sum();
    (sum_squares / samples.len() as f32).sqrt()
}

#[derive(Debug, Clone)]
pub struct SilenceAnalysis {
    pub min_rms: f32,
    pub max_rms: f32,
    pub avg_rms: f32,
    pub silence_regions: Vec<SilenceRegion>,
    pub reduction_percent: f32,
    pub original_duration: Duration,
    pub new_duration: Duration,
}

#[derive(Debug, Clone)]
pub struct SilenceRegion {
    pub start_frame: usize,
    pub end_frame: usize,
}
```

---

## UI State Management

### Application Structure

```rust
// src/app.rs

pub struct App {
    // Core state
    current_screen: Screen,

    // Recording state
    recorder: Option<AudioRecorder>,
    recording_duration: Option<Duration>,
    last_recording_path: Option<PathBuf>,

    // Transcription state
    transcription_result: Option<String>,
    is_transcribing: bool,

    // Pipeline state
    selected_pipeline: Option<Pipeline>,
    post_processed_result: Option<String>,

    // Configuration
    config: Config,

    // Communication channels
    message_rx: mpsc::Receiver<AppMessage>,
    message_tx: mpsc::Sender<AppMessage>,

    // Logging
    execution_log: Vec<String>,

    // Error state
    error_message: Option<String>,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum Screen {
    Recording,
    Settings,
    Pipelines,
}

pub enum AppMessage {
    RecordingStarted,
    RecordingStopped { path: PathBuf },
    TranscriptionComplete { text: String },
    PipelineComplete { text: String },
    Error { message: String },
    Log { message: String },
}

impl App {
    pub fn new(cc: &eframe::CreationContext) -> Self {
        let (tx, rx) = mpsc::channel(100);

        // Load config
        let config = Config::load().unwrap_or_default();

        Self {
            current_screen: Screen::Recording,
            recorder: None,
            recording_duration: None,
            last_recording_path: None,
            transcription_result: None,
            is_transcribing: false,
            selected_pipeline: None,
            post_processed_result: None,
            config,
            message_rx: rx,
            message_tx: tx,
            execution_log: Vec::new(),
            error_message: None,
        }
    }

    pub fn start_recording(&mut self) {
        let config = self.config.audio.clone();
        let output_dir = PathBuf::from("./recordings");

        match AudioRecorder::new(config, output_dir) {
            Ok(mut recorder) => {
                if let Err(e) = recorder.start_recording() {
                    self.error_message = Some(format!("Failed to start recording: {}", e));
                } else {
                    self.recorder = Some(recorder);
                    self.message_tx.try_send(AppMessage::RecordingStarted).ok();
                }
            }
            Err(e) => {
                self.error_message = Some(format!("Failed to create recorder: {}", e));
            }
        }
    }

    pub fn stop_recording(&mut self) {
        if let Some(mut recorder) = self.recorder.take() {
            let tx = self.message_tx.clone();

            tokio::spawn(async move {
                match recorder.stop_recording() {
                    Ok(path) => {
                        tx.send(AppMessage::RecordingStopped { path }).await.ok();
                    }
                    Err(e) => {
                        tx.send(AppMessage::Error {
                            message: format!("Failed to stop recording: {}", e)
                        }).await.ok();
                    }
                }
            });
        }
    }

    pub fn transcribe(&mut self, audio_path: PathBuf) {
        let api_key = self.config.whisper.api_key.clone();
        let tx = self.message_tx.clone();
        let silence_config = self.config.silence_removal.clone();

        self.is_transcribing = true;

        tokio::spawn(async move {
            // Step 1: Load audio
            let mut audio = match AudioBuffer::from_wav(&audio_path) {
                Ok(a) => a,
                Err(e) => {
                    tx.send(AppMessage::Error {
                        message: format!("Failed to load audio: {}", e)
                    }).await.ok();
                    return;
                }
            };

            // Step 2: Remove silence if enabled
            if silence_config.enabled {
                let remover = SilenceRemover::new(
                    silence_config.threshold,
                    silence_config.min_duration_ms,
                );

                match remover.remove_silence(&audio) {
                    Ok((processed, analysis)) => {
                        tx.send(AppMessage::Log {
                            message: format!(
                                "Silence removed: {:.1}% reduction (RMS: min={:.4}, max={:.4}, avg={:.4})",
                                analysis.reduction_percent,
                                analysis.min_rms,
                                analysis.max_rms,
                                analysis.avg_rms
                            )
                        }).await.ok();

                        audio = processed;

                        // Save processed audio
                        let processed_path = audio_path.with_extension("processed.wav");
                        audio.to_wav(&processed_path).ok();
                    }
                    Err(e) => {
                        tx.send(AppMessage::Error {
                            message: format!("Silence removal failed: {}", e)
                        }).await.ok();
                        return;
                    }
                }
            }

            // Step 3: Transcribe
            let client = WhisperClient::new(api_key);
            let request = TranscriptionRequest {
                file_path: audio_path,
                model: "whisper-1".to_string(),
                language: None,
                prompt: None,
                temperature: None,
                response_format: None,
            };

            match client.transcribe(request).await {
                Ok(response) => {
                    tx.send(AppMessage::TranscriptionComplete {
                        text: response.text
                    }).await.ok();
                }
                Err(e) => {
                    tx.send(AppMessage::Error {
                        message: format!("Transcription failed: {}", e)
                    }).await.ok();
                }
            }
        });
    }

    pub fn update(&mut self, ctx: &egui::Context) {
        // Process messages from background tasks
        while let Ok(msg) = self.message_rx.try_recv() {
            match msg {
                AppMessage::RecordingStarted => {
                    self.execution_log.push("Recording started".to_string());
                }
                AppMessage::RecordingStopped { path } => {
                    self.last_recording_path = Some(path.clone());
                    self.execution_log.push(format!("Recording saved: {:?}", path));

                    // Auto-transcribe
                    self.transcribe(path);
                }
                AppMessage::TranscriptionComplete { text } => {
                    self.transcription_result = Some(text);
                    self.is_transcribing = false;
                    self.execution_log.push("Transcription complete".to_string());
                }
                AppMessage::PipelineComplete { text } => {
                    self.post_processed_result = Some(text);
                    self.execution_log.push("Pipeline processing complete".to_string());
                }
                AppMessage::Error { message } => {
                    self.error_message = Some(message.clone());
                    self.execution_log.push(format!("Error: {}", message));
                }
                AppMessage::Log { message } => {
                    self.execution_log.push(message);
                }
            }
        }

        // Update recording duration
        if let Some(recorder) = &self.recorder {
            self.recording_duration = recorder.duration();
            ctx.request_repaint(); // Continuous updates while recording
        }
    }
}
```

---

## Configuration Persistence

```rust
// src/config/mod.rs

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Config {
    pub whisper: WhisperConfig,
    pub audio: AudioConfig,
    pub silence_removal: SilenceRemovalConfig,
    pub hotkeys: HotkeyConfig,
    pub ui: UiConfig,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            whisper: WhisperConfig::default(),
            audio: AudioConfig::default(),
            silence_removal: SilenceRemovalConfig::default(),
            hotkeys: HotkeyConfig::default(),
            ui: UiConfig::default(),
        }
    }
}

impl Config {
    pub fn load() -> Result<Self> {
        let path = Self::default_path();

        if !path.exists() {
            let config = Self::default();
            config.save()?;
            return Ok(config);
        }

        let content = std::fs::read_to_string(&path)?;
        let config: Config = toml::from_str(&content)?;
        Ok(config)
    }

    pub fn save(&self) -> Result<()> {
        let path = Self::default_path();

        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent)?;
        }

        let content = toml::to_string_pretty(self)?;
        std::fs::write(&path, content)?;

        tracing::info!("Config saved to {:?}", path);
        Ok(())
    }

    pub fn default_path() -> PathBuf {
        dirs::config_dir()
            .unwrap_or_else(|| PathBuf::from("."))
            .join("whispercat")
            .join("config.toml")
    }
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct WhisperConfig {
    pub api_key: String,
    pub model: String,
}

impl Default for WhisperConfig {
    fn default() -> Self {
        Self {
            api_key: String::new(),
            model: "whisper-1".to_string(),
        }
    }
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct SilenceRemovalConfig {
    pub enabled: bool,
    pub threshold: f32,
    pub min_duration_ms: u32,
    pub min_recording_duration_sec: u32,
}

impl Default for SilenceRemovalConfig {
    fn default() -> Self {
        Self {
            enabled: true,
            threshold: 0.01,
            min_duration_ms: 1500,
            min_recording_duration_sec: 10,
        }
    }
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct HotkeyConfig {
    pub record_toggle: String,
}

impl Default for HotkeyConfig {
    fn default() -> Self {
        Self {
            record_toggle: "Ctrl+Shift+R".to_string(),
        }
    }
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct UiConfig {
    pub theme: String,
    pub auto_paste: bool,
}

impl Default for UiConfig {
    fn default() -> Self {
        Self {
            theme: "Dark".to_string(),
            auto_paste: false,
        }
    }
}
```

---

## Next Steps

1. Initialize Cargo project with dependencies
2. Implement error types (src/error.rs)
3. Implement config module
4. Implement audio recording
5. Implement transcription client
6. Implement basic UI
7. Add silence removal
8. Add pipeline system
9. Add tests

This provides complete implementation details for the PoC.
