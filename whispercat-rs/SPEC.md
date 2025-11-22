# WhisperCat Rust PoC - Technical Specification

**Version:** 0.1.0 (Proof of Concept)
**Date:** 2025-11-22
**Status:** Specification Phase
**Author:** Claude (xblabs)

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Background & Context](#background--context)
3. [Goals & Non-Goals](#goals--non-goals)
4. [Architecture Overview](#architecture-overview)
5. [Technical Stack](#technical-stack)
6. [Core Features](#core-features)
7. [Implementation Phases](#implementation-phases)
8. [API Design](#api-design)
9. [File Structure](#file-structure)
10. [Key Learnings from Java Implementation](#key-learnings-from-java-implementation)
11. [Testing Strategy](#testing-strategy)
12. [Success Criteria](#success-criteria)
13. [Migration Path](#migration-path)
14. [Open Questions](#open-questions)

---

## Executive Summary

**Objective:** Create a Rust-based proof-of-concept for WhisperCat that demonstrates superior performance, reliability, and developer experience compared to the current Java implementation.

**Key Motivation:** The Java implementation (v1.5.0-xblabs) has proven the concept but faces inherent limitations:
- High memory footprint (JVM overhead)
- Slow startup time
- Platform-specific UI quirks (Swing/AWT)
- Dependency management complexity (Maven, JARs)
- Limited native integration

**Rust Benefits:**
- Native performance (no VM overhead)
- Memory safety without garbage collection
- Cross-platform UI with modern frameworks (egui, iced, slint)
- Better FFI for native audio APIs
- Single binary distribution
- Async/await for cleaner concurrent code

**Scope:** This PoC focuses on proving the core audio recording → transcription → post-processing pipeline in Rust, with a basic UI to demonstrate feasibility.

---

## Background & Context

### Current Java Implementation (v1.5.0-xblabs)

**What Works Well:**
- ✅ Multi-provider transcription (OpenAI, Faster-Whisper, Open WebUI)
- ✅ Pipeline-based post-processing architecture
- ✅ Silence removal with RMS analysis
- ✅ Pipeline optimization (merges consecutive same-model API calls)
- ✅ Drag-and-drop audio file support
- ✅ Global hotkeys for recording control
- ✅ System tray integration
- ✅ Comprehensive execution logging

**Pain Points:**
- ❌ Complex UI state management (Swing form lifecycle issues)
- ❌ Verbose Java code (getters/setters, verbose error handling)
- ❌ Maven dependency hell
- ❌ Large distribution size (~100MB with bundled JRE)
- ❌ Slow startup time (~2-3 seconds)
- ❌ Platform-specific quirks (drag-and-drop, system tray, notifications)
- ❌ Audio codec issues (OGG requires external ffmpeg)

**Core Concepts to Preserve:**
1. **Pipeline Architecture**: Units → Pipelines → Execution
2. **Provider Abstraction**: Multiple transcription services
3. **Silence Removal**: RMS-based audio preprocessing
4. **Pipeline Optimization**: Automatic API call batching
5. **Configuration Management**: Persistent user settings
6. **Console Logging**: Real-time execution visibility

---

## Goals & Non-Goals

### Goals (PoC Phase)

**Primary Goals:**
1. ✅ **Audio Recording** - Capture audio from system microphone
2. ✅ **Transcription** - Send audio to OpenAI Whisper API
3. ✅ **Basic UI** - Display transcription results
4. ✅ **Configuration** - Store API key persistently
5. ✅ **Performance Baseline** - Measure startup time, memory usage

**Secondary Goals:**
6. ✅ **Silence Removal** - Port RMS-based silence detection
7. ✅ **Pipeline System** - Demonstrate post-processing architecture
8. ✅ **Hotkeys** - Global recording hotkey support

### Non-Goals (Deferred to Full Implementation)

- ❌ Feature parity with Java version (too ambitious for PoC)
- ❌ Multiple transcription providers (focus on OpenAI only)
- ❌ Pipeline optimization (API batching)
- ❌ System tray integration
- ❌ Drag-and-drop support
- ❌ Unit library management
- ❌ Advanced UI customization

---

## Architecture Overview

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         UI Layer                            │
│  (egui/iced - native GUI with immediate mode rendering)   │
└─────────────────┬───────────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────────┐
│                    Application Core                         │
│  • Recording Manager (audio capture state machine)          │
│  • Transcription Service (async API client)                 │
│  • Pipeline Executor (post-processing)                      │
│  • Config Manager (persistent settings)                     │
└─────────────────┬───────────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────────┐
│                   Infrastructure Layer                      │
│  • Audio I/O (cpal for cross-platform recording)            │
│  • HTTP Client (reqwest for API calls)                      │
│  • File System (std::fs for config/audio files)             │
│  • Global Hotkeys (global-hotkey crate)                     │
└─────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Key Types |
|-----------|---------------|-----------|
| **UI** | Render UI, handle user input, display state | `App`, `RecordingScreen`, `SettingsScreen` |
| **RecordingManager** | State machine for audio recording lifecycle | `RecordingState`, `AudioRecorder` |
| **TranscriptionService** | Send audio to Whisper API, handle responses | `WhisperClient`, `TranscriptionRequest` |
| **PipelineExecutor** | Execute post-processing pipelines | `Pipeline`, `Unit`, `Executor` |
| **ConfigManager** | Load/save user settings (API keys, preferences) | `Config`, `Settings` |
| **AudioProcessor** | Silence removal, audio format conversion | `SilenceRemover`, `AudioBuffer` |

---

## Technical Stack

### Core Dependencies

```toml
[dependencies]
# UI Framework (choose one for PoC)
egui = "0.24"              # Immediate mode GUI (recommended for PoC)
eframe = "0.24"            # egui + windowing
# OR
# iced = "0.12"            # Elm-inspired reactive GUI

# Audio
cpal = "0.15"              # Cross-platform audio I/O
hound = "3.5"              # WAV file reading/writing

# HTTP & API
reqwest = { version = "0.11", features = ["json", "multipart"] }
tokio = { version = "1", features = ["full"] }

# Serialization
serde = { version = "1", features = ["derive"] }
serde_json = "1"
toml = "0.8"               # For config files

# Utilities
anyhow = "1"               # Error handling
thiserror = "1"            # Custom error types
tracing = "0.1"            # Logging
tracing-subscriber = "0.3"

# Platform-specific
[target.'cfg(any(target_os = "windows", target_os = "linux", target_os = "macos"))'.dependencies]
global-hotkey = "0.5"      # Global keyboard shortcuts
```

### Why These Choices?

**egui vs iced:**
- **egui (Recommended)**: Immediate mode, simpler for PoC, fast iteration
- **iced**: Reactive (Elm-style), better for complex UIs, steeper learning curve

**cpal for Audio:**
- Cross-platform (Windows/Linux/macOS)
- Low-level control over audio streams
- Active maintenance and good documentation

**tokio for Async:**
- Industry standard async runtime
- Required by reqwest
- Excellent ecosystem support

---

## Core Features

### 1. Audio Recording

**Requirements:**
- Enumerate available microphones
- Start/stop recording on hotkey press
- Record to WAV file (16kHz, mono, 16-bit PCM for optimal speech transcription)
- Real-time duration indicator
- Error handling (microphone access denied, no device found)

**State Machine:**
```rust
enum RecordingState {
    Idle,
    Recording {
        start_time: Instant,
        stream: Option<Stream>,
        buffer: AudioBuffer,
    },
    Processing,
    Error(String),
}
```

**API Design:**
```rust
struct AudioRecorder {
    state: RecordingState,
    config: AudioConfig,
    output_dir: PathBuf,
}

impl AudioRecorder {
    pub fn new(config: AudioConfig) -> Result<Self>;
    pub fn start_recording(&mut self, device: &Device) -> Result<()>;
    pub fn stop_recording(&mut self) -> Result<PathBuf>; // Returns WAV file path
    pub fn is_recording(&self) -> bool;
    pub fn duration(&self) -> Duration;
}
```

### 2. Transcription Service

**Requirements:**
- Send WAV file to OpenAI Whisper API
- Handle 25MB file size limit (pre-check before upload)
- Support async/await pattern
- Parse JSON response
- Error handling (API errors, network issues, rate limits)

**API Design:**
```rust
#[derive(Clone)]
struct WhisperClient {
    api_key: String,
    client: reqwest::Client,
}

impl WhisperClient {
    pub fn new(api_key: String) -> Self;

    pub async fn transcribe(&self, audio_file: &Path) -> Result<TranscriptionResponse>;

    pub async fn transcribe_with_options(
        &self,
        audio_file: &Path,
        options: TranscriptionOptions,
    ) -> Result<TranscriptionResponse>;
}

#[derive(Debug)]
struct TranscriptionResponse {
    pub text: String,
    pub duration: Option<f64>,
}

#[derive(Default)]
struct TranscriptionOptions {
    pub language: Option<String>,
    pub temperature: Option<f32>,
    pub prompt: Option<String>,
}
```

### 3. Silence Removal

**Requirements:**
- RMS-based amplitude analysis
- Configurable threshold and minimum duration
- 100ms window analysis
- Preserve audio quality
- Detailed logging (min/max/avg RMS)

**Port from Java:**
```java
// Java implementation: SilenceRemover.java (lines 190-256)
// Key algorithm:
// 1. Analyze audio in 100ms windows
// 2. Calculate RMS for each window
// 3. Mark windows below threshold as silence
// 4. Remove silence regions >= min duration
// 5. Splice remaining audio together
```

**Rust API Design:**
```rust
struct SilenceRemover {
    threshold: f32,         // 0.001 - 0.050 RMS
    min_duration_ms: u32,   // Minimum silence duration to remove
}

impl SilenceRemover {
    pub fn new(threshold: f32, min_duration_ms: u32) -> Self;

    pub fn remove_silence(&self, audio: &AudioBuffer) -> Result<AudioBuffer>;

    pub fn analyze(&self, audio: &AudioBuffer) -> SilenceAnalysis;
}

struct SilenceAnalysis {
    pub min_rms: f32,
    pub max_rms: f32,
    pub avg_rms: f32,
    pub silence_regions: Vec<SilenceRegion>,
    pub reduction_percent: f32,
}

struct SilenceRegion {
    pub start_frame: usize,
    pub end_frame: usize,
}
```

### 4. Pipeline System (Simplified for PoC)

**Requirements:**
- Define processing units (Prompt, TextReplacement)
- Chain units into pipelines
- Execute sequentially
- Log each step

**API Design:**
```rust
#[derive(Clone, Serialize, Deserialize)]
struct Pipeline {
    pub id: Uuid,
    pub name: String,
    pub units: Vec<Unit>,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(tag = "type")]
enum Unit {
    Prompt {
        name: String,
        provider: Provider,
        model: String,
        system_prompt: String,
        user_prompt: String,
    },
    TextReplacement {
        name: String,
        find: String,
        replace: String,
        regex: bool,
    },
}

#[derive(Clone, Serialize, Deserialize)]
enum Provider {
    OpenAI,
    // Future: FasterWhisper, OpenWebUI
}

struct PipelineExecutor {
    openai_client: OpenAIClient,
}

impl PipelineExecutor {
    pub async fn execute(&self, input: &str, pipeline: &Pipeline) -> Result<String>;
}
```

### 5. Configuration Management

**Requirements:**
- Store API keys securely
- Persist user preferences
- Support multiple config locations (user dir, project dir)
- TOML format for human readability

**Config Structure:**
```toml
[whisper]
api_key = "sk-..."
provider = "OpenAI"

[audio]
sample_rate = 16000
channels = 1
selected_device = "Default"

[silence_removal]
enabled = true
threshold = 0.010
min_duration_ms = 1500
min_recording_duration_sec = 10

[hotkeys]
record_toggle = "Ctrl+R"

[ui]
theme = "Dark"
auto_paste = true
```

**API Design:**
```rust
#[derive(Serialize, Deserialize, Clone)]
struct Config {
    pub whisper: WhisperConfig,
    pub audio: AudioConfig,
    pub silence_removal: SilenceRemovalConfig,
    pub hotkeys: HotkeyConfig,
    pub ui: UiConfig,
}

impl Config {
    pub fn load() -> Result<Self>;
    pub fn save(&self) -> Result<()>;
    pub fn default_path() -> PathBuf;
}
```

---

## Implementation Phases

### Phase 0: Project Setup (Day 1)

**Tasks:**
- ✅ Create `whispercat-rs/` directory
- ✅ Write this specification document
- ⬜ Initialize Cargo project: `cargo init --name whispercat`
- ⬜ Set up basic dependencies in `Cargo.toml`
- ⬜ Configure logging with `tracing`
- ⬜ Set up CI/CD (GitHub Actions for build + test)

**Deliverable:** Compiles and runs "Hello World"

### Phase 1: Audio Recording (Days 2-3)

**Tasks:**
- ⬜ Implement `AudioRecorder` with cpal
- ⬜ Device enumeration
- ⬜ Start/stop recording
- ⬜ Write to WAV file with hound
- ⬜ Basic error handling
- ⬜ Unit tests for audio buffer handling

**Deliverable:** CLI that records 5 seconds of audio and saves to WAV

### Phase 2: Transcription Integration (Day 4)

**Tasks:**
- ⬜ Implement `WhisperClient` with reqwest
- ⬜ Multipart form upload
- ⬜ Parse JSON response
- ⬜ File size validation (25MB limit)
- ⬜ Error handling (API errors, network failures)
- ⬜ Integration test (mock server)

**Deliverable:** CLI that transcribes a WAV file

### Phase 3: Basic UI (Days 5-6)

**Tasks:**
- ⬜ Set up egui/iced framework
- ⬜ Recording screen with start/stop button
- ⬜ Display transcription result
- ⬜ Settings screen for API key
- ⬜ Status indicator (recording/transcribing/idle)

**Deliverable:** GUI app that records and transcribes

### Phase 4: Silence Removal (Day 7)

**Tasks:**
- ⬜ Port RMS calculation from Java
- ⬜ Implement window-based analysis
- ⬜ Splice audio (remove silence regions)
- ⬜ Diagnostic logging (min/max/avg RMS)
- ⬜ Unit tests with known audio samples

**Deliverable:** Silence removal working with configurable threshold

### Phase 5: Pipeline System (Days 8-9)

**Tasks:**
- ⬜ Define Pipeline/Unit data structures
- ⬜ Implement PipelineExecutor
- ⬜ OpenAI API client for chat completions
- ⬜ Sequential execution with logging
- ⬜ Save/load pipelines to disk

**Deliverable:** Post-processing with a simple pipeline

### Phase 6: Polish & Documentation (Day 10)

**Tasks:**
- ⬜ Global hotkey support
- ⬜ Config file persistence
- ⬜ Error messages in UI
- ⬜ README with setup instructions
- ⬜ Performance benchmarks vs Java
- ⬜ Demo video

**Deliverable:** Polished PoC ready for evaluation

---

## API Design

### Error Handling Strategy

Use `anyhow::Result` for application-level errors and `thiserror` for domain-specific errors:

```rust
use thiserror::Error;

#[derive(Error, Debug)]
pub enum WhisperCatError {
    #[error("Audio recording failed: {0}")]
    RecordingError(String),

    #[error("Transcription API error: {status} - {message}")]
    ApiError { status: u16, message: String },

    #[error("File too large: {size} MB (limit: 25 MB)")]
    FileSizeError { size: f64 },

    #[error("Configuration error: {0}")]
    ConfigError(String),

    #[error("Audio processing error: {0}")]
    AudioProcessingError(String),
}

pub type Result<T> = std::result::Result<T, WhisperCatError>;
```

### Async Patterns

Use Tokio's async/await with channels for UI communication:

```rust
use tokio::sync::mpsc;

enum AppMessage {
    RecordingStarted,
    RecordingStopped { file_path: PathBuf },
    TranscriptionComplete { text: String },
    Error { message: String },
}

// In UI
let (tx, mut rx) = mpsc::channel(32);

// In background task
tokio::spawn(async move {
    let result = transcribe(audio_file).await;
    tx.send(AppMessage::TranscriptionComplete { text: result }).await.ok();
});

// In UI update loop
if let Ok(msg) = rx.try_recv() {
    match msg {
        AppMessage::TranscriptionComplete { text } => {
            self.transcription = text;
        }
        // ...
    }
}
```

---

## File Structure

```
whispercat-rs/
├── Cargo.toml
├── Cargo.lock
├── README.md
├── SPEC.md                    # This document
├── src/
│   ├── main.rs                # Entry point, app initialization
│   ├── lib.rs                 # Public library interface
│   ├── app.rs                 # Main application state
│   ├── ui/
│   │   ├── mod.rs
│   │   ├── recording_screen.rs
│   │   ├── settings_screen.rs
│   │   └── components.rs      # Reusable UI components
│   ├── audio/
│   │   ├── mod.rs
│   │   ├── recorder.rs        # AudioRecorder
│   │   ├── processor.rs       # SilenceRemover
│   │   └── buffer.rs          # AudioBuffer helpers
│   ├── transcription/
│   │   ├── mod.rs
│   │   ├── whisper.rs         # WhisperClient
│   │   └── types.rs           # Request/response types
│   ├── pipeline/
│   │   ├── mod.rs
│   │   ├── executor.rs        # PipelineExecutor
│   │   ├── unit.rs            # Unit definitions
│   │   └── optimizer.rs       # Future: API call batching
│   ├── config/
│   │   ├── mod.rs
│   │   └── manager.rs         # Config load/save
│   └── error.rs               # Error types
├── tests/
│   ├── integration_test.rs
│   └── fixtures/
│       └── test_audio.wav
└── docs/
    ├── ARCHITECTURE.md
    └── MIGRATION.md
```

---

## Key Learnings from Java Implementation

### 1. UI State Management

**Java Problem:**
```java
// MainForm.java lines 98-107 (OLD CODE - BUGGY)
if (index != 0 && recorderForm != null) {
    recorderForm.stopRecording(true);  // ← Kills recording!
    recorderForm = null;                // ← Destroys instance!
}
```

**Lesson:** Don't destroy UI components on navigation. Preserve state.

**Rust Solution:**
```rust
// Use enum for screens, preserve state in app struct
enum Screen {
    Recording,
    Settings,
    Pipelines,
}

struct App {
    current_screen: Screen,
    recording_state: RecordingState,  // ← Persists across screens
    transcription: String,             // ← Persists across screens
    // ...
}
```

### 2. Configuration Persistence

**Java Problem:**
Sliders didn't auto-save, leading to user confusion about whether changes were applied.

**Lesson:** Auto-save preferences immediately on change.

**Rust Solution:**
```rust
// In slider onChange handler
if ui.add(egui::Slider::new(&mut self.silence_threshold, 0.001..=0.050)).changed() {
    self.config.silence_removal.threshold = self.silence_threshold;
    self.config.save().ok(); // Auto-save
}
```

### 3. Pipeline Optimization

**Java Success:**
```java
// PostProcessingService.java lines 413-455
// Automatically merge consecutive same-model API calls
// Example: 3 units → 1 API call = 66% cost reduction
```

**Lesson:** This feature is valuable, but complex. Defer to full implementation.

**Rust Strategy:** Focus on correct sequential execution first. Optimize later.

### 4. Silence Removal Diagnostics

**Java Success:**
```java
// SilenceRemover.java lines 245-253
// Show actual RMS values (min/max/avg) in console
console.log(String.format("Audio RMS analysis: min=%.4f, max=%.4f, avg=%.4f",
    minRMS, maxRMS, avgRMS));
```

**Lesson:** Users need visibility into audio levels to tune threshold.

**Rust Solution:** Port this exact diagnostic output. It's essential for UX.

### 5. File Size Validation

**Java Lesson (added in v1.5.0):**
```java
// Check file size BEFORE uploading to avoid wasted API calls
if (fileSizeBytes > 25 * 1024 * 1024) {
    // Show error with actionable suggestions
}
```

**Rust Solution:** Build this in from day 1.

---

## Testing Strategy

### Unit Tests

**Priority Areas:**
1. ✅ Audio buffer manipulation
2. ✅ RMS calculation (compare with Java results)
3. ✅ Silence detection algorithm
4. ✅ Config serialization/deserialization
5. ✅ Pipeline execution logic

**Example:**
```rust
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_rms_calculation() {
        let samples = vec![0.0, 0.1, 0.2, 0.1, 0.0];
        let rms = calculate_rms(&samples);
        assert!((rms - 0.1095).abs() < 0.001);
    }

    #[test]
    fn test_silence_removal() {
        let audio = load_test_audio("fixtures/test_with_silence.wav");
        let remover = SilenceRemover::new(0.01, 1500);
        let result = remover.remove_silence(&audio).unwrap();
        assert!(result.duration() < audio.duration());
    }
}
```

### Integration Tests

**Scenarios:**
1. Record → Save → Verify WAV format
2. Transcribe (mock API) → Verify response parsing
3. Config load → Modify → Save → Load again → Verify persistence

### Performance Benchmarks

**Metrics to Track:**
- Startup time (Rust vs Java)
- Memory usage (idle, recording, transcribing)
- Transcription latency (same audio file, both implementations)
- Silence removal processing time
- Binary size (Rust vs Java JAR)

**Benchmark Tool:**
```rust
use criterion::{black_box, criterion_group, criterion_main, Criterion};

fn bench_silence_removal(c: &mut Criterion) {
    let audio = load_test_audio("fixtures/recording_10s.wav");
    let remover = SilenceRemover::new(0.01, 1500);

    c.bench_function("remove_silence_10s", |b| {
        b.iter(|| remover.remove_silence(black_box(&audio)))
    });
}

criterion_group!(benches, bench_silence_removal);
criterion_main!(benches);
```

---

## Success Criteria

### PoC Success Metrics

**Functional Requirements:**
- ✅ Records audio from microphone
- ✅ Transcribes via OpenAI Whisper API
- ✅ Displays results in GUI
- ✅ Persists API key configuration
- ✅ Removes silence with configurable threshold

**Performance Requirements:**
- ✅ Startup time < 500ms (vs Java ~2-3s)
- ✅ Memory usage < 50MB (vs Java ~150MB)
- ✅ Silence removal throughput > 10x real-time

**Code Quality:**
- ✅ All core modules have unit tests
- ✅ No `unwrap()` in production code (use proper error handling)
- ✅ Documentation for public APIs
- ✅ Clippy passes with no warnings

**User Experience:**
- ✅ Intuitive UI (minimal learning curve)
- ✅ Clear error messages
- ✅ Responsive (no UI freezing during processing)

---

## Migration Path

### Decision Points

After PoC completion, evaluate:

1. **Performance Gains**: Is Rust meaningfully faster/leaner?
2. **Development Velocity**: How quickly can we port features?
3. **UI Quality**: Does egui/iced match Java Swing quality?
4. **Ecosystem Maturity**: Are Rust audio/UI crates production-ready?

### Full Migration Strategy (If PoC Succeeds)

**Phase 1: Core Parity (Months 1-2)**
- Port all Java features to Rust
- Focus on Recording, Transcription, Pipelines
- Maintain feature parity with v1.5.0-xblabs

**Phase 2: UI Polish (Month 3)**
- Replicate Java UI layouts
- System tray integration
- Drag-and-drop support
- Global hotkeys

**Phase 3: New Features (Month 4+)**
- Pipeline optimization (API batching)
- Advanced audio processing
- Plugin system
- Real-time transcription

**Deprecation Plan:**
- Release Rust version as v2.0.0
- Maintain Java version for 6 months (security fixes only)
- Migrate users with migration guide

---

## Open Questions

### Technical Decisions

1. **UI Framework: egui vs iced vs slint?**
   - **Recommendation:** Start with egui for PoC (simpler, faster iteration)
   - Re-evaluate for full implementation based on UI complexity needs

2. **Audio Backend: cpal vs rodio?**
   - **Recommendation:** cpal (lower-level, more control for silence removal)

3. **Config Location: User home vs XDG dirs?**
   - **Recommendation:** Use `dirs` crate (cross-platform standard locations)

4. **Async Runtime: Tokio vs async-std?**
   - **Recommendation:** Tokio (better ecosystem support, required by reqwest)

5. **Distribution: Single binary vs installer?**
   - **PoC:** Single binary (cargo build --release)
   - **Full:** Consider cargo-bundle for platform-specific installers

### Feature Scope

1. **Should PoC include multiple transcription providers?**
   - **Answer:** No, focus on OpenAI only. Add providers in full implementation.

2. **Should PoC include pipeline optimization?**
   - **Answer:** No, sequential execution is sufficient for PoC.

3. **Should PoC include system tray?**
   - **Answer:** No, focus on core functionality first.

---

## References

### Java Implementation

- **Repository:** https://github.com/xblabs/whispercat-xb
- **Branch:** `claude/configurable-post-processing-models-01TPmSAeY17peDgwgq8FXrbQ`
- **Version:** v1.5.0-xblabs
- **Key Files:**
  - `src/main/java/org/whispercat/recording/SilenceRemover.java` (lines 190-317)
  - `src/main/java/org/whispercat/postprocessing/PostProcessingService.java` (lines 410-490)
  - `src/main/java/org/whispercat/MainForm.java` (lines 92-147)

### Rust Resources

- **egui:** https://github.com/emilk/egui
- **cpal:** https://github.com/RustAudio/cpal
- **reqwest:** https://github.com/seanmonstar/reqwest
- **The Rust Book:** https://doc.rust-lang.org/book/
- **Tokio Tutorial:** https://tokio.rs/tokio/tutorial

### OpenAI API

- **Whisper API Docs:** https://platform.openai.com/docs/api-reference/audio
- **Rate Limits:** https://platform.openai.com/docs/guides/rate-limits
- **File Size:** 25 MB maximum

---

## Appendix A: Java to Rust Translation Guide

### Common Patterns

**Java:**
```java
public class RecorderForm extends JPanel {
    private boolean isRecording = false;
    private JTextArea transcriptionArea;

    public void startRecording() {
        isRecording = true;
        recordButton.setEnabled(false);
    }
}
```

**Rust:**
```rust
pub struct RecordingScreen {
    is_recording: bool,
    transcription: String,
}

impl RecordingScreen {
    pub fn start_recording(&mut self) {
        self.is_recording = true;
    }

    pub fn ui(&mut self, ui: &mut egui::Ui) {
        ui.add_enabled(!self.is_recording, egui::Button::new("Start Recording"));
    }
}
```

### Error Handling

**Java:**
```java
try {
    String result = whisperClient.transcribe(file);
    transcriptionArea.setText(result);
} catch (IOException e) {
    logger.error("Transcription failed", e);
    showError("Transcription failed: " + e.getMessage());
}
```

**Rust:**
```rust
match whisper_client.transcribe(&file).await {
    Ok(result) => {
        self.transcription = result.text;
    }
    Err(e) => {
        tracing::error!("Transcription failed: {}", e);
        self.error_message = Some(format!("Transcription failed: {}", e));
    }
}
```

### Configuration

**Java:**
```java
Properties properties = new Properties();
properties.load(new FileInputStream(configFile));
String apiKey = properties.getProperty("apiKey");
```

**Rust:**
```rust
let config = std::fs::read_to_string(config_path)?;
let config: Config = toml::from_str(&config)?;
let api_key = config.whisper.api_key;
```

---

## Appendix B: Performance Targets

### Baseline (Java v1.5.0-xblabs)

| Metric | Value |
|--------|-------|
| Startup time | ~2-3 seconds |
| Memory (idle) | ~150 MB |
| Memory (recording) | ~180 MB |
| JAR size | ~15 MB |
| With JRE | ~100 MB |
| Silence removal (10s audio) | ~300ms |

### Targets (Rust PoC)

| Metric | Target | Stretch Goal |
|--------|--------|--------------|
| Startup time | < 500ms | < 100ms |
| Memory (idle) | < 50 MB | < 20 MB |
| Memory (recording) | < 70 MB | < 40 MB |
| Binary size | < 10 MB | < 5 MB |
| Silence removal (10s audio) | < 100ms | < 50ms |

---

## Appendix C: Checklist for Agent Handoff

**For the implementing agent, verify you have:**

- ✅ Read and understood the entire specification
- ✅ Reviewed Java implementation (key files in References section)
- ✅ Set up Rust development environment (cargo, rustc 1.70+)
- ✅ Cloned whispercat-xb repository for reference
- ✅ Have access to OpenAI API key for testing
- ✅ Understand the phase-by-phase approach
- ✅ Know where to find help (Rust resources in References)

**Before starting Phase 1, confirm:**

- ✅ Project compiles: `cargo build`
- ✅ Tests pass: `cargo test`
- ✅ Logging works: `cargo run` shows tracing output
- ✅ Dependencies are up-to-date: `cargo update`

**Success criteria for handoff:**

- ✅ All phases completed (0-6)
- ✅ Performance benchmarks pass (Appendix B)
- ✅ Documentation updated (README.md)
- ✅ Demo video recorded
- ✅ Code review by original agent (me)

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 0.1.0 | 2025-11-22 | Claude (xblabs) | Initial specification document |

---

**END OF SPECIFICATION**

---

## Quick Start for Implementing Agent

```bash
# 1. Navigate to Rust PoC directory
cd whispercat-rs

# 2. Initialize project
cargo init --name whispercat

# 3. Add dependencies (copy from "Technical Stack" section)
# Edit Cargo.toml

# 4. Start with Phase 1: Audio Recording
# Create src/audio/mod.rs and src/audio/recorder.rs

# 5. Run and test
cargo run
cargo test

# 6. Refer back to this spec frequently!
```

Good luck! 🦀
