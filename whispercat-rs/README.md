# WhisperCat Rust

<p align="center">
  <img src="../whispercat.svg" alt="WhisperCat Icon" width="200"/>
</p>

<p align="center">
  <img alt="Rust Edition" src="https://img.shields.io/badge/Rust-2021-orange?style=flat-square&logo=rust&logoColor=white" />
  <img alt="MIT License" src="https://img.shields.io/badge/License-MIT-blue?style=flat-square&logo=github&logoColor=white" />
  <img alt="Platform" src="https://img.shields.io/badge/Platform-Linux%20%7C%20Windows%20%7C%20macOS-lightgrey?style=flat-square" />
</p>

**WhisperCat Rust** is a complete rewrite of WhisperCat in Rust, offering superior performance, memory safety, and cross-platform compatibility. This modern implementation provides AI-powered audio transcription with advanced post-processing pipelines, all wrapped in a sleek, customizable UI.

---

## ✨ Key Features

### 🎯 Core Capabilities
- **🎤 Audio Recording** - Capture audio with configurable quality settings
- **🤖 AI Transcription** - Support for multiple providers:
  - OpenAI Whisper API
  - Faster-Whisper (local/self-hosted)
  - Open WebUI integration
- **⚡ Processing Pipelines** - Chain multiple post-processing steps
- **🔄 Auto-Paste** - Automatically paste transcription results
- **🎹 Global Hotkeys** - System-wide keyboard shortcuts with sequence support
- **🔕 Silence Removal** - Intelligent audio preprocessing to reduce costs

### 🏗️ Advanced Architecture

#### Processing Unit Library System
- **Reusable Units** - Create processing units once, use in multiple pipelines
- **Two Unit Types**:
  - **Prompt Units** - AI-powered text transformation (OpenAI, Open WebUI, Faster-Whisper)
  - **Text Replacement** - Regex-based find/replace operations
- **Enable/Disable** - Toggle units and entire pipelines on/off
- **Centralized Management** - Library-based storage with UUID tracking

#### Multi-Provider Support
- **OpenAI** - Industry-standard Whisper API
- **Faster-Whisper** - Local deployment for privacy
- **Open WebUI** - Flexible web-based interface
- **Model Discovery** - Automatically fetch available models from providers
- **Per-Pipeline Providers** - Different providers for different processing steps

### 🎨 User Interface

#### Modern Design
- **Custom Color Scheme**:
  - Side panels: `#1E1F22` (navigation, logs)
  - Main panel: `#26282D` (content area)
- **150ms Animations** - Snappy, responsive UI transitions
- **Toast Notifications** - Non-intrusive feedback system
- **Real-time Logs** - Structured execution log with timestamps

#### Advanced Configuration
- **🎹 Visual Hotkey Editor** - Record hotkeys with live preview
- **🔑 Key Sequence Support** - VS Code-style multi-key combinations
  - Single keys: `Ctrl+Shift+R`
  - Sequences: `Ctrl+K, Ctrl+S`
- **🔊 Audio Testing** - Built-in microphone test with level visualization
- **📡 Model Fetching** - Browse and select models from providers
- **⚙️ Per-Provider Settings** - Customized configuration for each service

### 🚀 Performance & Quality

#### Silence Removal
- **RMS-based Detection** - Intelligent silence identification
- **Configurable Thresholds** - Adjust sensitivity and duration
- **Cost Reduction** - 20-40% smaller files = lower API costs
- **File Size Management** - Stay under provider limits

#### System Integration
- **System Tray** - Minimize to tray (ready to enable with full implementation)
- **Global Hotkeys** - Works across all applications
- **Auto-Start** - Optional startup integration
- **Clipboard Integration** - Seamless copy/paste workflow

---

## 🚀 Quick Start

### Prerequisites

**Rust Toolchain:**
```bash
# Install Rust (if not already installed)
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

# Verify installation
rustc --version
cargo --version
```

**System Dependencies:**

**Linux (Debian/Ubuntu):**
```bash
sudo apt update
sudo apt install -y \
    libasound2-dev \
    pkg-config \
    build-essential
```

**macOS:**
```bash
# No additional dependencies required
# CoreAudio is included with macOS
```

**Windows:**
```bash
# No additional dependencies required
# Uses Windows Audio Session API (WASAPI)
```

### Building from Source

1. **Clone the Repository:**
```bash
git clone https://github.com/xblabs/whispercat-xb.git
cd whispercat-xb/whispercat-rs
```

2. **Build the Project:**
```bash
# Debug build (faster compilation)
cargo build

# Release build (optimized)
cargo build --release
```

3. **Run the Application:**
```bash
# Debug build
cargo run

# Release build
./target/release/whispercat
```

---

## ⚙️ Configuration

### First Launch Setup

1. **Navigate to Settings** - Click the "⚙️ Settings" tab
2. **Choose Provider** - Select OpenAI, Faster-Whisper, or Open WebUI
3. **Configure Provider**:
   - **OpenAI**: Enter API key
   - **Faster-Whisper**: Set server URL
   - **Open WebUI**: Configure URL and API key
4. **Fetch Models** (Optional) - Click "🔄 Fetch Models" to browse available models
5. **Test Audio** (Optional) - Use "🎤 Test Microphone" to verify input

### Configuration File

Settings are stored in:
- **Linux**: `~/.config/whispercat/config.toml`
- **macOS**: `~/Library/Application Support/whispercat/config.toml`
- **Windows**: `%APPDATA%\whispercat\config.toml`

**Example Configuration:**
```toml
[whisper]
api_key = "sk-..."
provider = "OpenAI"
model = "whisper-1"

[audio]
sample_rate = 16000
channels = 1
format = "S16LE"

[silence_removal]
enabled = true
threshold = 0.01
min_duration_ms = 1500

[hotkeys]
record_toggle = "Ctrl+Shift+R"

[ui]
theme = "dark"
auto_paste = true

[[processing_units]]
id = "550e8400-e29b-41d4-a716-446655440000"
name = "Grammar Correction"
unit_type = { Prompt = { provider = "OpenAI", model = "gpt-4", system_prompt = "Fix grammar", user_prompt_template = "{{input}}" } }

[[pipelines]]
id = "660e8400-e29b-41d4-a716-446655440000"
name = "My Pipeline"
enabled = true
unit_refs = [
  { unit_id = "550e8400-e29b-41d4-a716-446655440000", enabled = true }
]
```

---

## 📖 Usage

### Recording Audio

1. **Start Recording**:
   - Press `Ctrl+Shift+R` (or your configured hotkey)
   - OR click "🎙 Start Recording" button
2. **Stop Recording**:
   - Press hotkey again
   - OR click "⏹ Stop Recording" button
3. **Auto-Transcription**: Transcription starts automatically

### Using Pipelines

1. **Create Processing Units** (Settings → Pipelines → Create Unit):
   - **Prompt Unit**: AI-powered transformation
   - **Text Replacement**: Find/replace with regex support

2. **Build Pipeline** (Settings → Pipelines → New Pipeline):
   - Add units from library
   - Reorder by dragging
   - Enable/disable individual units
   - Test with sample text

3. **Run Pipeline**:
   - Record or paste text
   - Select pipeline from dropdown
   - Click "▶ Run Pipeline"
   - View results in real-time

### Hotkey Sequences

**Single Key Combination:**
```
Ctrl+Shift+R
Alt+F1
Super+Space
```

**Multi-Key Sequence (VS Code style):**
```
Ctrl+K, Ctrl+S    (Press Ctrl+K, release, then press Ctrl+S within 1 second)
Ctrl+X, Ctrl+C
Alt+A, Alt+B, Alt+C
```

**Recording Hotkeys:**
1. Go to Settings → Hotkey Configuration
2. Click "🎹 Record Hotkey"
3. Press desired key combination(s)
4. Click "⏹ Stop Recording"
5. Changes save automatically

---

## 🏗️ Architecture

### Project Structure

```
whispercat-rs/
├── src/
│   ├── main.rs              # Application entry point
│   ├── app.rs               # Main application logic
│   ├── config/              # Configuration management
│   │   └── mod.rs           # Config loading/saving
│   ├── audio/               # Audio capture & processing
│   │   ├── mod.rs           # Audio module exports
│   │   ├── recorder.rs      # Audio recording
│   │   ├── processor.rs     # Silence removal
│   │   └── compressor.rs    # Audio compression
│   ├── transcription/       # Transcription clients
│   │   ├── mod.rs           # Transcription exports
│   │   ├── types.rs         # Request/response types
│   │   ├── client.rs        # API client
│   │   └── models.rs        # Model discovery
│   ├── pipeline/            # Processing pipeline system
│   │   └── mod.rs           # Pipeline execution
│   ├── hotkey/              # Global hotkey management
│   │   └── mod.rs           # Hotkey registration
│   ├── tray/                # System tray integration
│   │   └── mod.rs           # Tray manager (stub)
│   ├── notifications.rs     # Toast notification system
│   ├── autopaste.rs         # Clipboard integration
│   ├── logger.rs            # Structured logging
│   ├── error.rs             # Error types
│   └── ui/                  # User interface
│       └── mod.rs           # egui UI components
├── Cargo.toml               # Rust dependencies
├── Cargo.lock               # Locked dependency versions
└── README.md                # This file
```

### Technology Stack

- **UI Framework**: [egui](https://github.com/emilk/egui) - Immediate mode GUI
- **Audio**: [cpal](https://github.com/RustAudio/cpal) - Cross-platform audio I/O
- **HTTP Client**: [reqwest](https://github.com/seanmonstar/reqwest) - Async HTTP
- **Async Runtime**: [tokio](https://tokio.rs/) - Async execution
- **Serialization**: [serde](https://serde.rs/) - Config & API serialization
- **Hotkeys**: [global-hotkey](https://github.com/tauri-apps/global-hotkey) - Global keyboard shortcuts
- **Audio Processing**: [hound](https://github.com/ruuda/hound) - WAV encoding/decoding

### Processing Pipeline Flow

```
┌─────────────┐
│   Record    │
│   Audio     │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   Silence   │
│   Removal   │ (Optional)
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ Transcribe  │ ◄── OpenAI / Faster-Whisper / Open WebUI
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  Pipeline   │
│  Execution  │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   Unit 1    │ ◄── Prompt / Text Replacement
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   Unit 2    │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  Auto-Paste │ (Optional)
│    Result   │
└─────────────┘
```

---

## 🔧 Development

### Running Tests

```bash
# Run all tests
cargo test

# Run specific test
cargo test test_parse_hotkey

# Run with output
cargo test -- --nocapture

# Run tests in release mode
cargo test --release
```

### Code Quality

```bash
# Format code
cargo fmt

# Lint with clippy
cargo clippy

# Check without building
cargo check

# Build documentation
cargo doc --open
```

### Adding Features

1. **Create Feature Branch**:
```bash
git checkout -b feature/my-new-feature
```

2. **Implement Feature**:
   - Add code in appropriate module
   - Write unit tests
   - Update documentation

3. **Test & Format**:
```bash
cargo test
cargo fmt
cargo clippy
```

4. **Commit & Push**:
```bash
git add .
git commit -m "feat: add my new feature"
git push origin feature/my-new-feature
```

### Debugging

**Enable Logging:**
```bash
# Set log level
export RUST_LOG=debug
cargo run

# Trace level (very verbose)
export RUST_LOG=trace
cargo run

# Module-specific logging
export RUST_LOG=whispercat::pipeline=debug
cargo run
```

**Debug Build:**
```bash
# Build with debug symbols
cargo build

# Run with debugger (GDB on Linux)
rust-gdb target/debug/whispercat

# Or LLDB on macOS
rust-lldb target/debug/whispercat
```

---

## 🎯 Feature Roadmap

### ✅ Implemented (Phase 1-3)
- [x] Processing Unit Library system
- [x] Per-unit enable/disable toggles
- [x] Per-pipeline enable/disable
- [x] Open WebUI support
- [x] Toast notifications
- [x] Model fetching from providers
- [x] System tray integration (stub)
- [x] Key sequence support
- [x] Visual hotkey configuration
- [x] Audio testing UI

### 🚧 Planned
- [ ] Full system tray implementation (requires GTK on Linux)
- [ ] Real-time audio level monitoring
- [ ] Pipeline templates/presets
- [ ] Export/import pipelines
- [ ] Batch processing
- [ ] Custom audio formats (MP3, FLAC, etc.)
- [ ] Plugin system
- [ ] Multi-language UI
- [ ] Cloud sync for settings

---

## 🐛 Known Issues

### System Tray (Linux)
The system tray feature is currently a stub implementation. To enable full tray support:

1. Install GTK3 dependencies:
```bash
sudo apt install libgtk-3-dev libappindicator3-dev
```

2. Uncomment tray-icon in `Cargo.toml`:
```toml
[target.'cfg(any(target_os = "windows", target_os = "macos"))'.dependencies]
tray-icon = "0.14"
```

3. Uncomment implementation in `src/tray.rs`

### Audio Device Selection
- Some systems may show duplicate audio devices
- Use "Test Microphone" to verify correct device

### Hotkey Conflicts
- Global hotkeys may conflict with other applications
- Try different key combinations if hotkey doesn't register

---

## 📝 License

This project is licensed under the **MIT License** - see the [LICENSE](../LICENSE) file for details.

---

## 🙏 Acknowledgments

- **[egui](https://github.com/emilk/egui)** - Excellent immediate mode GUI framework
- **[cpal](https://github.com/RustAudio/cpal)** - Cross-platform audio library
- **[OpenAI Whisper API](https://openai.com/research/whisper)** - State-of-the-art transcription
- **[Faster-Whisper](https://github.com/SYSTRAN/faster-whisper)** - Optimized local inference
- **[Open WebUI](https://openwebui.com/)** - Flexible web interface

---

## 🤝 Contributing

Contributions are welcome! 🎉

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'feat: add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

**Contribution Guidelines:**
- Follow Rust best practices and idioms
- Write tests for new features
- Update documentation
- Run `cargo fmt` and `cargo clippy` before committing
- Use conventional commits (feat, fix, docs, style, refactor, test, chore)

---

## 📧 Contact & Support

- **Issues**: [GitHub Issues](https://github.com/xblabs/whispercat-xb/issues)
- **Discussions**: [GitHub Discussions](https://github.com/xblabs/whispercat-xb/discussions)
- **Original Project**: [WhisperCat](https://github.com/ddxy/whispercat)

---

<p align="center">
  Made with ❤️ and 🦀 by the WhisperCat community
</p>
