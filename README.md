# WhisperCat

<p align="center">
  <img src="whispercat.svg" alt="WhisperCat Icon" width="350"/>
</p>

<p align="center">
  <img alt="Latest Version" src="https://img.shields.io/badge/Latest%20Version-v1.6.0--xblabs-brightgreen?style=flat-square&logo=github&logoColor=white" />
  <a href="LICENSE" target="https://opensource.org/license/mit">
    <img alt="MIT License" src="https://img.shields.io/badge/License-MIT-blue?style=flat-square&logo=github&logoColor=white" />
  </a>
  <img alt="Windows" src="https://img.shields.io/badge/Windows-Compatible-blue?style=flat-square&logo=windows&logoColor=white" />
  <img alt="Linux" src="https://img.shields.io/badge/Linux-Compatible-yellow?style=flat-square&logo=linux&logoColor=white" />
  <img alt="macOS" src="https://img.shields.io/badge/macOS-Planned-black?style=flat-square&logo=apple&logoColor=white" />
</p>

WhisperCat is your personal companion for capturing audio, transcribing it, and managing it in one seamless interface. Whether you're taking notes, working on voice memos, or transcribing conversations, WhisperCat makes it simple and efficient. Maximize your productivity with adjustable hotkeys, background operation, and full control over your settings.

---

## 🚀 xblabs Enhanced Fork

This is an enhanced fork by [xblabs](https://github.com/xblabs) with significant improvements and new features beyond the original WhisperCat project. This fork focuses on enhanced usability, flexibility, and power-user features.

### Key Enhancements in xblabs Fork

✨ **New Architecture:**
- **Processing Unit Library** - Create reusable processing units (prompt transformations, text replacements) stored in a centralized library
- **Pipeline System** - Build custom pipelines by combining units from the library, with full enable/disable and reordering control
- **Automatic Pipeline Optimization** - Intelligently merges consecutive same-model API calls into single chained prompts, reducing costs by 2-3x. [Read the full story →](docs/PIPELINE_OPTIMIZATION.md)
- **Unit Editor** - Streamlined workflow for creating and editing processing units with auto-save
- **Inline Unit Creation** - "Create New Unit" button in Pipeline Editor for quick unit creation and immediate pipeline integration

🎯 **Visibility & Monitoring:**
- **Console Execution Log** - Real-time timestamped log showing detailed pipeline execution:
  - Transcription start/completion with file info
  - Pipeline start/end with unit counts
  - Step-by-step progress (Step 1/3, 2/3, etc.)
  - Compiled prompts (system + user) sent to APIs
  - API call status and errors
- **System-Level Notifications** - Subtle OS corner pop-ups for transcription and pipeline completion (works when app is minimized)
- **Refined Toast Notifications** - Pastel colors, reduced padding, only for high-level events

🔧 **UX Improvements:**
- **Responsive Layout** - Controls (paste from clipboard, enable post-processing, etc.) now flow horizontally with responsive wrapping
- **Dark Mode Default** - Application starts in dark mode for reduced eye strain
- **Configurable Post-Processing Models** - Add any OpenAI model (GPT-5 nano, mini, etc.) via GUI settings
- **Drag & Drop Audio Files** - Drop WAV, MP3, OGG, M4A, FLAC files directly into the app for transcription
- **OGG File Support** - Automatic conversion of Telegram voice notes and other OGG files
- **Smart Recording Activation** - Hotkey automatically switches to recorder screen from any menu and **auto-saves settings** when triggered from Options screen
- **Sorted Lists** - Alphabetically organized pipelines and units for easy navigation
- **Advanced Large File Support** - **MP3 compression (10x+ reduction)** using ffmpeg for files exceeding 24MB, extends max recording length from 10 to 100+ minutes

💡 **Intelligent Audio Processing:**
- **MP3 Compression for Large Files** (NEW 🎵) - When recordings exceed OpenAI's 24MB limit:
  - Automatically converts to MP3 using ffmpeg with optimized speech settings
  - **10x+ compression ratio** (24MB WAV → 2-3MB MP3) vs 2-3x with old downsampling method
  - **Extends max recording length from ~10 minutes to 100+ minutes**
  - Uses VBR quality 4 (~140kbps), mono, 16kHz sample rate for excellent speech quality
  - Supports all OpenAI formats: MP3, M4A, OGG, FLAC, WAV with automatic content-type detection
  - Falls back to legacy downsampling if ffmpeg unavailable
  - Transparent - works automatically, no user configuration needed
- **Automatic Silence Removal** - RMS-based silence detection removes pauses/silence before transcription
  - Reduces file size by 20-40% for typical recordings with reading/thinking pauses
  - Lowers transcription costs (less audio = fewer tokens)
  - Avoids hitting OpenAI's 25MB file limit
  - Advanced settings UI with sliders for threshold (0.001-0.050) and duration (500-10000ms)
  - **Auto-save sliders** - Changes save immediately without clicking Save button
  - **Real-time diagnostics** - Shows actual audio RMS values (min/max/avg) to help tune settings
  - **Smart warnings** - Detects when audio is too quiet or threshold needs adjustment
  - **Quiet speaker optimization** - Detailed guidance for users who speak softly
  - Configurable minimum recording duration (skip silence removal for short clips)
  - Detailed console logging shows reduction statistics and RMS analysis
  - Optional compressed file retention for debugging

### xblabs Fork Changelog

#### Latest Update (2025-11-22)
- **MP3 Compression for Large Files** 🎵:
  - Automatic MP3 compression using ffmpeg when files exceed 24MB
  - **10x+ compression ratio** vs WAV format (24MB WAV → 2-3MB MP3)
  - **Extends maximum recording length from ~10 minutes to 100+ minutes** for OpenAI transcription
  - Uses optimized settings for speech: VBR quality 4 (~140kbps), mono, 16kHz sample rate
  - Falls back to downsampling if ffmpeg is unavailable
  - Supports all OpenAI-compatible formats: MP3, M4A, OGG, FLAC, WAV
- **Robust Error Handling & Timeouts** ⏱️:
  - Fixed JsonParseException when OpenAI returns non-JSON error responses
  - Added 10-minute socket timeout to prevent indefinite hanging on large file uploads
  - Enhanced error logging with response truncation for better debugging
  - Graceful handling of network timeouts and API errors
- **Recording Hotkey from Options Screen** ⚙️:
  - Recording hotkey now works from Options/Settings screen
  - **Auto-saves all settings** before switching to recording screen
  - Prevents bad UX where users talk without feedback
  - Hotkey only disabled when actively configuring keybind fields (has focus)
- **Critical UI State Persistence Fixes** 🔧:
  - Recording no longer stops when switching screens (continues in background)
  - Transcription text persists when navigating between Record/Options screens
  - Execution log persists across screen switches
  - Silence threshold sliders auto-save on change (no Save button required)
  - Fixed transcript duplication bug when auto-paste is enabled
- **Enhanced Silence Removal Diagnostics** 🔍:
  - Real-time RMS analysis logging (min/max/avg) for debugging detection issues
  - Smart detection of quiet recordings with actionable suggestions
  - Console output shows: "Audio RMS analysis: min=0.0012, max=0.0419, avg=0.0020"
  - Helpful warnings when threshold doesn't match audio levels
  - Optimized for quiet speakers with adjustable thresholds
- **Intelligent Silence Removal** 💡:
  - Automatic detection and removal of silence/pauses before transcription
  - RMS amplitude analysis with 100ms window-based detection
  - Default parameters: 1500ms minimum duration, 0.01 (-40dB) threshold
  - Reduces file size by 20-40% for typical recordings
  - Advanced settings UI with sliders for threshold (0.001-0.050) and duration (500-10000ms)
  - Detailed console logging: original duration, detected silence, reduction %, compressed duration
  - Configurable minimum recording duration threshold (skip silence removal for short clips)
  - Optional compressed file retention toggle
  - Automatic application to all recordings before transcription
  - Benefits: lower costs, avoid 25MB limit, better transcription quality
- **Improved Pipeline Optimization Logging** ⚡:
  - Clearer header: "⚡ PIPELINE OPTIMIZATION ACTIVE"
  - Shows exact benefit: "2 API calls saved, 66% cost reduction"
  - Numbered list of units being merged
  - Better visual separation and success confirmation

#### Update (2025-11-21)
- **Major Architecture Overhaul**:
  - Introduced Processing Unit Library for reusable post-processing components
  - Pipelines now reference units instead of embedding steps directly
  - Unit Editor with auto-save and "Create New Unit" in-pipeline workflow
- **Automatic Pipeline Optimization** 🚀:
  - Intelligently chains consecutive same-model API calls into single requests
  - Reduces costs by 2-3x and execution time by 2-3x for typical pipelines
  - Uses explicit variable naming (Style 1) for reliable chaining semantics
  - Fully automatic - no configuration needed
  - Detailed console logging shows optimization decisions
  - [Complete technical deep-dive](docs/PIPELINE_OPTIMIZATION.md)
- **Console Execution Log**: Real-time detailed logging with timestamps for full pipeline visibility
- **System-Level Notifications**: OS corner pop-ups for transcription/pipeline completion
- **Dark Mode Default**: Application now starts in dark mode
- **Responsive Layout**: Controls flow horizontally with responsive wrapping (flexbox-style)
- **Refined Toast Notifications**: Pastel colors, significantly reduced padding, high-level events only
- **Configurable Models**: Add custom OpenAI models via Settings GUI
- **Drag & Drop**: Drop audio files (WAV, MP3, OGG, M4A, FLAC) directly for transcription
- **OGG Support**: Auto-convert Telegram voice notes (.ogg files)
- **Smart Hotkeys**: Recording activation works from any screen
- **File Size Fix**: Auto-compress large audio files (>24MB) before upload

**Repository:** [https://github.com/xblabs/whispercat-xb](https://github.com/xblabs/whispercat-xb)

---

## Features
- **v1.4.0: Open Web UI Support**:  
  WhisperCat now supports transcription via the Open Web UI, a flexible and user-friendly web interface that provides powerful transcription services. This integration allows you to process your recordings using modern, cloud-based technologies and even leverage free, open-source models for transcription. For more details about configuration and available models, please visit [openwebui.com](https://openwebui.com/).
- **v1.3.0: FasterWhisper Server Support**:  
  Now WhisperCat supports transcription via FasterWhisper Server. Please refer to the [installation instructions](https://speaches.ai/installation/#__tabbed_1_3) for setting up the FasterWhisper Server. Note that the previous GitHub repository for FasterWhisper is outdated. The new repository is available at [github.com/speaches-ai/speaches](https://github.com/speaches-ai/speaches/).
- **Record Audio**: Capture sound using your chosen microphone.
- **Automated Transcription**: Process and transcribe your recordings with OpenAI Whisper API.
- **Post-Processing**: Enhance the generated speech-to-text output by:
  Applying text replacements to clean up or adjust the transcript.
  Performing an additional query to OpenAI to refine and improve the text.
  Combining these post-processing steps in any order for optimal results.
- **Global Hotkey Support**:
    - Start/stop recording using a global hotkey combination (e.g., `CTRL + R`).
    - Alternatively, use a hotkey sequence (e.g., triple `ALT`) to start/stop recording.
- **Background Mode**: Minimize WhisperCat to the system tray, allowing it to run in the background.
- **Microphone Test Functionality**: Ensure you've selected the correct microphone before recording.
- **Notifications**: Receive notifications for important events, such as recording start/stop or errors.
- **GUI for Settings Management**:
    - Enter your API key for Whisper transcription.
    - Choose and test a microphone.
    - Customize preferences, including hotkeys.
- **Dark Mode Support**: Switch between light and dark themes.

---

## Screenshot

Here's what WhisperCat looks like in action:

<p align="center">
  <a href="https://github.com/ddxy/whispercat/blob/master/screenshot.png?raw=true" target="_blank">
    <img src="https://github.com/ddxy/whispercat/blob/master/screenshot.png?raw=true" alt="WhisperCat Desktop Screenshot" width="80%" />
  </a>
</p>

---

## Installation

1. Visit the **[Releases Page](https://github.com/ddxy/whispercat/releases)** for the WhisperCat project.
2. Download the latest version for your operating system and follow the setup instructions.

### Optional: ffmpeg for MP3 Compression

To enable **MP3 compression** for large audio files (recommended for recordings >10 minutes):

**Windows:**
1. Download ffmpeg from [ffmpeg.org/download.html](https://ffmpeg.org/download.html) or use [Chocolatey](https://chocolatey.org/): `choco install ffmpeg`
2. Add ffmpeg to your system PATH

**Linux:**
```sh
# Ubuntu/Debian
sudo apt-get install ffmpeg

# Fedora/RHEL
sudo dnf install ffmpeg

# Arch Linux
sudo pacman -S ffmpeg
```

**macOS:**
```sh
# Using Homebrew
brew install ffmpeg
```

**Verify installation:**
```sh
ffmpeg -version
```

**Benefits of ffmpeg:**
- **10x+ better compression** than fallback method (2-3MB vs 24MB)
- **100+ minute recordings** instead of ~10 minutes for OpenAI transcription
- Automatic MP3 conversion with optimized speech settings
- Works transparently - if not installed, app falls back to legacy downsampling

---

## Future Ideas

Here are some planned ideas and features for future releases:
- **FasterWhisper Server Enhancements**:Now that FasterWhisper Server support is integrated, future updates might include advanced configuration options and performance tweaks.
- **Add Post Processing Text to Audio**: Add the ability to add post processing text to audio, e.g. via ElevenLabs, Amazon Polly and more.
- **Groq Whisperer**: Add support for Groq Whisperer Server.
- **Groq and Anthropic Post-Processing**: Add support for Groq and Anthropic Post-Processing.
- **macOS Support**: While full macOS support is planned, an **experimental version** is already available. Check it out here: [Experimental macOS Build](https://github.com/ddxy/whispercat/releases/tag/v1.0.0). Feedback is welcome!
- **Microphone Selection Improvements**: Revamp the microphone selection process to make it more user-friendly and intuitive.
- **Icon Fixes**: Refine and improve icons and UI graphics for better display on all platforms.
- **Audio Format Options**: Allow users to choose the output audio format (e.g., WAV, MP3).
- **Multiple Language Support**: Expand GUI and transcription support to more languages.
- **Custom Shortcuts**: Add the ability to configure custom hotkeys for various actions.
- **Audio Playback**: Integrate audio playback functionality for recorded files directly within the audioRecorderUI.
- **Continuous Recording Mode**: Enable a mode for long-term recording sessions with automatic splitting of large files.

Feel free to contribute any of these features or suggest new ones in the issues section!

---

## Development & Compilation

For developers who want to contribute to WhisperCat or build from source, follow these comprehensive instructions:

### Prerequisites

**Required Software:**
- **Java Development Kit (JDK) 11 or higher** - [Download from Oracle](https://www.oracle.com/java/technologies/downloads/) or [Adoptium](https://adoptium.net/)
- **Apache Maven 3.6+** - [Download from Apache Maven](https://maven.apache.org/download.cgi)
- **Git** - [Download from git-scm.com](https://git-scm.com/downloads)

**Verify Installation:**
```sh
# Check Java version (should be 11 or higher)
java -version

# Check Maven installation
mvn -version

# Check Git installation
git --version
```

### Building from Source

1. **Clone the Repository:**

    ```sh
    # Original repository
    git clone https://github.com/ddxy/whispercat.git

    # OR clone the xblabs enhanced fork
    git clone https://github.com/xblabs/whispercat-xb.git
    ```

2. **Navigate to Project Directory:**

    ```sh
    cd whispercat-xb  # or 'whispercat' for original
    ```

3. **Build the Project with Maven:**

    ```sh
    # Clean and package (skips tests)
    mvn clean package -DskipTests

    # OR build with tests
    mvn clean package
    ```

    This creates a JAR file in the `target/` directory:
    - `Audiorecorder-1.0-SNAPSHOT-jar-with-dependencies.jar`

4. **Run the Application:**

    ```sh
    # Run directly with Maven
    mvn exec:java -Dexec.mainClass="org.whispercat.AudioRecorderUI"

    # OR run the compiled JAR
    java -jar target/Audiorecorder-1.0-SNAPSHOT-jar-with-dependencies.jar
    ```

### Development Workflow

**IDE Setup:**
- **IntelliJ IDEA**: Open as Maven project
- **Eclipse**: Import as "Existing Maven Project"
- **VS Code**: Install "Java Extension Pack" and "Maven for Java"

**Common Maven Commands:**
```sh
# Clean build artifacts
mvn clean

# Compile without running tests
mvn compile -DskipTests

# Run tests only
mvn test

# Generate IDE project files
mvn idea:idea     # for IntelliJ
mvn eclipse:eclipse  # for Eclipse

# View dependency tree
mvn dependency:tree
```

### Project Structure

```
whispercat-xb/
├── src/
│   └── main/
│       ├── java/
│       │   └── org/whispercat/
│       │       ├── AudioRecorderUI.java
│       │       ├── ConfigManager.java
│       │       ├── MainForm.java
│       │       ├── postprocessing/
│       │       ├── recording/
│       │       └── settings/
│       └── resources/
│           ├── whispercat.svg
│           └── icon/
├── pom.xml
└── README.md
```

### Troubleshooting Build Issues

**Problem: Maven not found**
```sh
# Add Maven to PATH (example for Linux/Mac)
export PATH=/path/to/maven/bin:$PATH

# For Windows, add to System Environment Variables
```

**Problem: Java version mismatch**
```sh
# Check JAVA_HOME
echo $JAVA_HOME  # Linux/Mac
echo %JAVA_HOME%  # Windows

# Set JAVA_HOME if needed
export JAVA_HOME=/path/to/jdk  # Linux/Mac
set JAVA_HOME=C:\path\to\jdk   # Windows
```

**Problem: Dependencies not downloading**
```sh
# Force update dependencies
mvn clean install -U

# Clear Maven cache
rm -rf ~/.m2/repository/*
```

---

## Usage

1. **Start the Application:**

    ```sh
    mvn exec:java -Dexec.mainClass="org.whispercat.AudioRecorderUI"
    ```

2. **Configure the Application:**
    - Open the settings dialog via the menu.
    - Enter your API key for Whisper transcription.
    - Select and test the desired microphone.
    - Configure the FasterWhisper Server settings (URL, model, and language) if using FasterWhisper.
    - Customize other settings such as hotkeys and notifications.

3. **Start Recording:**
    - Use the configured global hotkey or hotkey sequence to begin recording.

---

## Known Issues

- **Microphone Selection**:  
  Due to the Java audio implementation, more audio devices may be listed than are actually available. Use the "Test Microphone" feature to identify and verify the correct device.

---

## License

This project is licensed under the **MIT License**.

---

## Acknowledgements

- **[OpenAI Whisper API](https://openai.com/whisper)** for providing a powerful transcription engine.
- **[FasterWhisper Server](https://github.com/speaches-ai/speaches/)** – please note that the previous repository is outdated; refer to the new repository for the latest installation instructions: [Installation Guide](https://speaches.ai/installation/#__tabbed_1_3).
- **[SVG Repo](https://www.svgrepo.com/collection/news/)** for vector graphic resources, including the project icon.
- https://www.svgrepo.com/svg/523073/trash-bin-minimalistic
- https://www.svgrepo.com/svg/522526/edit
- https://www.svgrepo.com/collection/flat-ui-icons/
- https://github.com/DJ-Raven/flatlaf-dashboard
- https://www.svgrepo.com/collection/noto-emojis/

---

## Contributing

Contributions to WhisperCat are welcome! 🎉
- Open an issue to report bugs or suggest new features.
- Submit a pull request to contribute fixes or new functionality.

---

## Contact

For questions, feedback, or support, open an **issue** on the [GitHub repository](https://github.com/ddxy/whispercat).
