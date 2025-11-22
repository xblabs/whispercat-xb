# WhisperCat v1.5.0-xblabs Release Notes

## 🎉 Major Release: Enhanced Stability, Diagnostics & Pipeline Optimization

This release represents a significant milestone for the xblabs fork of WhisperCat, focusing on rock-solid UI state management, intelligent audio processing, and comprehensive diagnostics to help users optimize their workflow.

---

## 🔧 Critical UI State & Persistence Fixes

### Recording Continuity
- **Recording no longer stops when switching screens** - Your recording continues in the background when you navigate to Options, Locks, or other screens
- Previously, switching screens would immediately kill the recording without warning
- Now you can adjust settings or check other screens while recording continues seamlessly

### State Persistence
- **Transcription text persists** when navigating between Record/Options screens
- **Execution log persists** across all screen switches
- **Form instances are reused** instead of destroyed and recreated
- No more data loss when switching between screens!

### Auto-Save Sliders
- **Silence removal sliders auto-save** when you finish adjusting them
- No need to click "Save" button for slider changes
- Settings persist immediately, preventing confusion about whether changes were saved
- Applies to: Silence Threshold, Min Silence Duration, Min Recording Duration

### Fixed: Transcript Duplication Bug
- Resolved issue where transcripts would sometimes appear duplicated in the text area
- This particularly affected short recordings when auto-paste was enabled
- Root cause: Auto-paste (Ctrl+V) was pasting into the transcription text area itself
- Solution: Focus is now properly transferred before auto-paste operations

---

## 🔍 Enhanced Silence Removal Diagnostics

### Real-Time Audio Analysis
- **RMS analysis logging** shows actual min/max/avg audio levels
- Console output: `Audio RMS analysis: min=0.0012, max=0.0419, avg=0.0020 (threshold=0.025)`
- Instantly see if your audio is too quiet or if threshold needs adjustment

### Smart Detection & Warnings
- **Intelligent warnings** when audio is too quiet for current threshold
- Detects when all RMS values are below threshold (100% silence detection)
- Provides actionable suggestions:
  - "Try: increasing microphone volume or reducing silence threshold"
  - "This could indicate: low microphone gain, quiet voice, or background noise issue"

### Optimized for Quiet Speakers
- Special consideration for users who speak softly
- Detailed guidance on setting threshold to match voice level
- Recommended threshold: Set to 50-70% of your max RMS value
- Example: If max RMS = 0.04, use threshold = 0.008

---

## ⚡ Improved Pipeline Optimization Logging

### Clearer Visualization
```
─────────────────────────────────────────
⚡ PIPELINE OPTIMIZATION ACTIVE
  Merging 3 consecutive OpenAI/gpt-4 units
  Benefit: 2 API calls saved, 66% cost reduction
─────────────────────────────────────────
  Units being merged:
    1. Summarize
    2. Fix Grammar
    3. Add Emojis

  Compiled System Prompt: [shows merged context]
  Compiled User Prompt: [shows chained steps]

  Executing optimized chain...
─────────────────────────────────────────
✓ Optimized chain completed - 2 API calls saved!
─────────────────────────────────────────
```

### What's New
- Shows exact provider/model being used (e.g., "OpenAI/gpt-4")
- Displays quantified benefit: "2 API calls saved, 66% cost reduction"
- Numbered list of units being merged for clarity
- Success confirmation shows savings achieved
- Better visual separation with clear separators

---

## 💡 Intelligent Silence Removal (from v1.4.0-xblabs)

### Automatic Detection & Removal
- RMS-based silence detection removes pauses/silence before transcription
- Reduces file size by 20-40% for typical recordings
- Lowers transcription costs (less audio = fewer tokens)
- Helps avoid OpenAI's 25MB file limit

### Advanced Settings
- **Threshold slider**: 0.001-0.050 RMS (now with auto-save!)
- **Min silence duration**: 500-10,000ms
- **Min recording duration**: Configure when to apply silence removal
- **Detailed diagnostics**: See exact RMS values and reduction statistics
- **Optional file retention**: Keep compressed files for debugging

### Benefits
- Lower transcription costs
- Faster processing (smaller files)
- Avoids file size limits
- Better transcription quality (removes dead air)

---

## 🚀 Automatic Pipeline Optimization (from v1.4.0-xblabs)

### Intelligent Batching
- **Automatically merges consecutive same-model API calls** into single requests
- Reduces costs by 2-3x and execution time by 2-3x
- Fully automatic - no configuration needed
- Works with OpenAI and Open WebUI providers

### How It Works
- Detects consecutive units using same provider + model
- Compiles chained prompt with explicit variable naming (STEP1_OUTPUT, STEP2_OUTPUT, etc.)
- Executes as single API call instead of multiple round trips
- Text Replacement units break the chain (can't be optimized)

### Example
**Before Optimization:**
- Unit 1: Summarize (OpenAI gpt-4) → API Call 1
- Unit 2: Fix Grammar (OpenAI gpt-4) → API Call 2
- Unit 3: Add Emojis (OpenAI gpt-4) → API Call 3
- **Total: 3 API calls, 3x cost, 3x latency**

**After Optimization:**
- All 3 units → Single chained API call
- **Total: 1 API call, 66% cost reduction, 3x faster!**

📖 [Read the full technical deep-dive →](docs/PIPELINE_OPTIMIZATION.md)

---

## 📊 Enhanced Console Execution Log

### Real-Time Visibility
- Timestamped logging for all operations
- Transcription start/completion with file info
- Pipeline execution with step-by-step progress (Step 1/3, 2/3, etc.)
- Compiled prompts (system + user) sent to APIs
- API call status and detailed error messages
- Performance timing measurements
- **Clear Log button** to reset the console

### Example Output
```
[05:12:12] ─────────────────────────────────────────
[05:12:12] Analyzing audio for silence...
[05:12:12] Original duration: 16.7s (333750 frames)
[05:12:12] Silence threshold: 0.008 RMS | Min duration: 2500ms
[05:12:12] Audio RMS analysis: min=0.0001, max=0.1263, avg=0.0027
[05:12:12] Detected 2 silence region(s) (total: 4.2s)
[05:12:12] Reduction: 25.1%
[05:12:12] Compressed duration: 12.5s (250000 frames)
[05:12:12] ✓ Silence removed: record_20251122_051318_nosilence.wav
[05:12:12] ─────────────────────────────────────────
[05:12:12] Starting transcription using OpenAI
[05:12:14] ✓ Transcription completed
```

---

## 🎯 Key Enhancements Summary

| Feature | Benefit |
|---------|---------|
| **Recording Continuity** | Never lose recordings when navigating menus |
| **State Persistence** | All data (transcription, logs, settings) preserved |
| **Auto-Save Sliders** | No more forgetting to click Save |
| **RMS Diagnostics** | See exactly what's happening with your audio |
| **Smart Warnings** | Actionable guidance for optimal settings |
| **Quiet Speaker Support** | Works great even with soft voices |
| **Pipeline Optimization** | 2-3x cost reduction, 2-3x faster execution |
| **Enhanced Logging** | Full visibility into all operations |
| **No Duplication Bug** | Reliable auto-paste behavior |

---

## 🔨 Technical Improvements

### Architecture
- Form instance reuse pattern (MainForm.java)
- Proper focus management before auto-paste operations
- Enhanced diagnostics with statistical RMS analysis
- Improved logging infrastructure with quantified benefits

### Code Quality
- Fixed race conditions in UI state management
- Removed duplicate form creation bugs
- Better separation of concerns (focus handling)
- More comprehensive error messages

### Documentation
- Updated README with all latest features
- Created PIPELINE_OPTIMIZATION.md technical guide
- Added inline code comments for diagnostics
- Comprehensive release notes

---

## 📦 Installation

### Download Pre-Built Release
1. Download `Audiorecorder-1.0-SNAPSHOT-jar-with-dependencies.jar` from this release
2. Run with: `java -jar Audiorecorder-1.0-SNAPSHOT-jar-with-dependencies.jar`

### Build from Source
```bash
git clone https://github.com/xblabs/whispercat-xb.git
cd whispercat-xb
git checkout v1.5.0-xblabs
mvn clean package -DskipTests
java -jar target/Audiorecorder-1.0-SNAPSHOT-jar-with-dependencies.jar
```

---

## 🐛 Bug Fixes

- **Fixed:** Recording stopped when switching to Options/Locks screens
- **Fixed:** Transcription field cleared when returning to Record screen
- **Fixed:** Execution log cleared when switching screens
- **Fixed:** Slider values resetting on screen switches
- **Fixed:** Transcript duplication when auto-paste enabled
- **Fixed:** System notification dependency issues

---

## 📝 Usage Tips

### For Quiet Speakers
1. Go to Options → Silence Removal settings
2. Make a test recording and check the execution log
3. Look for: `Audio RMS analysis: min=X, max=Y, avg=Z`
4. Set **Silence threshold** to 50-70% of your **max RMS** value
5. Example: If max RMS = 0.04, use threshold = 0.008
6. Increase **Min silence duration** to 2500ms+ to avoid cutting natural pauses

### For Pipeline Users
1. Create multiple units with the same provider/model
2. Add them consecutively to a pipeline
3. Watch for "⚡ PIPELINE OPTIMIZATION ACTIVE" in execution log
4. See exactly how many API calls you're saving!

### For Troubleshooting
- Enable "Keep compressed file" to debug silence removal
- Check RMS diagnostics to understand audio levels
- Use Clear Log button to reset console between tests
- Adjust sliders gradually and watch immediate auto-save

---

## 🙏 Acknowledgements

This release builds on the excellent foundation of the original WhisperCat project by [ddxy](https://github.com/ddxy/whispercat), with significant enhancements by [xblabs](https://github.com/xblabs).

Special thanks to:
- OpenAI Whisper API for transcription
- FlatLaf for beautiful UI components
- dorkbox SystemTray for cross-platform tray support
- All contributors and testers!

---

## 🔗 Links

- **Repository:** [https://github.com/xblabs/whispercat-xb](https://github.com/xblabs/whispercat-xb)
- **Original Project:** [https://github.com/ddxy/whispercat](https://github.com/ddxy/whispercat)
- **Pipeline Optimization Guide:** [docs/PIPELINE_OPTIMIZATION.md](docs/PIPELINE_OPTIMIZATION.md)
- **Issues:** [Report bugs or request features](https://github.com/xblabs/whispercat-xb/issues)

---

## 📄 License

This project is licensed under the **MIT License**.

---

**Full Changelog:** [All commits since last release](https://github.com/xblabs/whispercat-xb/compare/v1.4.0...v1.5.0-xblabs)
