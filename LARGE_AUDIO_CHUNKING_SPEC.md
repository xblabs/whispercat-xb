# Large Audio File Chunking Feature Specification

## Context & Current State

### Branch Information
**Current Branch:** `claude/manual-pipeline-runner-spec-01QHA2EN2MGkJaxYWgdYcToE`

**Latest Commits:**
- `29cc624` - feat: add Apply Settings button with clear feedback
- `de6f2f1` - fix: correct silence threshold description
- `71c893f` - feat: add manual pipeline runner with history tracking

**To Continue This Work:**
```bash
# For next Claude session:
git fetch origin
git checkout claude/manual-pipeline-runner-spec-01QHA2EN2MGkJaxYWgdYcToE
git pull origin claude/manual-pipeline-runner-spec-01QHA2EN2MGkJaxYWgdYcToE

# Create new branch for large audio chunking feature:
git checkout -b claude/large-audio-chunking-[NEW_SESSION_ID]

# After work is complete, merge back:
git checkout claude/manual-pipeline-runner-spec-01QHA2EN2MGkJaxYWgdYcToE
git merge claude/large-audio-chunking-[NEW_SESSION_ID]
```

---

## Feature Request: Large Audio File Chunking

### Use Case: ADHD-Friendly Meeting Analysis

**Scenario:**
1. User records a 2-hour Slack Huddle/Teams/Zoom meeting using Windows Game Bar (Win+G)
2. Windows saves recording as M4A/AAC (~90MB for 2 hours)
3. User drags file into WhisperCat for transcription
4. **Problem:** OpenAI Whisper API has 25MB file size limit
5. **Solution:** Automatically detect large files, split into chunks, transcribe sequentially, merge results

### Problem Statement

**Current Behavior:**
- Drag & drop accepts audio files without size validation
- Large files sent directly to API → fails with size limit error
- No pre-flight check on file size or duration
- User has no visibility into why transcription failed

**API Constraints:**

| Provider | Limit | Type |
|----------|-------|------|
| OpenAI Whisper | 25MB | File size |
| Faster-Whisper (local) | No limit | Memory dependent |
| Open WebUI | Varies | Server config |

**File Size Reality:**

| Format | 2-Hour Recording | Notes |
|--------|------------------|-------|
| WAV (44.1kHz, 16-bit, stereo) | ~1.2 GB | Uncompressed |
| WAV (16kHz, 16-bit, mono) | ~230 MB | Whisper-optimized |
| MP3 (128kbps) | ~115 MB | Common format |
| AAC/M4A (Windows Game Bar) | ~90 MB | Default Windows recording |
| OGG (Slack export) | ~80 MB | Variable bitrate |

---

## Proposed Solution: Smart Pre-Flight & Chunking

### Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Audio File Dropped                        │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   Pre-Flight Analysis                        │
│  • File size                                                 │
│  • Estimated duration (if determinable)                      │
│  • Format detection                                          │
│  • FFmpeg availability check                                 │
└─────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┴───────────────┐
              │                               │
         < 25MB                           > 25MB
              │                               │
              ▼                               ▼
┌─────────────────────┐       ┌─────────────────────────────┐
│  Normal Processing  │       │    Show Options Dialog      │
│  (current behavior) │       │                             │
└─────────────────────┘       │  Options:                   │
                              │  1. Split & transcribe      │
                              │  2. Compress first (ffmpeg) │
                              │  3. Use local Whisper       │
                              │  4. Cancel                  │
                              └─────────────────────────────┘
```

### User Interface: Large File Dialog

```
┌─────────────────────────────────────────────────────────────┐
│  Large Audio File Detected                              [X] │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  File: meeting_2024-11-28.m4a                               │
│  Size: 95 MB (exceeds 25MB API limit)                       │
│  Estimated duration: ~2 hours                               │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  How would you like to proceed?                             │
│                                                             │
│  ○ Split into chunks and transcribe sequentially            │
│    └─ Will create ~4 chunks of ~25 min each                 │
│    └─ Transcriptions will be merged automatically           │
│    └─ Estimated time: 8-12 minutes                          │
│                                                             │
│  ○ Compress with FFmpeg first, then transcribe              │
│    └─ Convert to MP3 mono 16kHz (~15MB estimated)           │
│    └─ Single transcription request                          │
│    └─ Requires: FFmpeg installed ✓                          │
│                                                             │
│  ○ Use Faster-Whisper (local) - no size limit               │
│    └─ Requires: Faster-Whisper server running               │
│    └─ Status: Not configured ✗                              │
│                                                             │
│  ☐ Remember my choice for files over 25MB                   │
│                                                             │
│                              [Proceed]  [Cancel]            │
└─────────────────────────────────────────────────────────────┘
```

---

## Technical Implementation

### 1. Pre-Flight Analysis

**New Class: `AudioFileAnalyzer.java`**

```java
package org.whispercat.recording;

public class AudioFileAnalyzer {

    public static class AnalysisResult {
        public long fileSizeBytes;
        public String format;  // wav, mp3, m4a, ogg, flac
        public Float estimatedDurationSeconds;  // null if unknown
        public boolean exceedsApiLimit;
        public boolean ffmpegAvailable;
        public boolean canSplitNatively;  // true for WAV
        public int suggestedChunkCount;
        public String[] availableOptions;
    }

    public static AnalysisResult analyze(File audioFile) {
        AnalysisResult result = new AnalysisResult();
        result.fileSizeBytes = audioFile.length();
        result.format = detectFormat(audioFile);
        result.estimatedDurationSeconds = estimateDuration(audioFile);
        result.exceedsApiLimit = result.fileSizeBytes > 25 * 1024 * 1024;
        result.ffmpegAvailable = checkFfmpegAvailable();
        result.canSplitNatively = "wav".equals(result.format);

        if (result.exceedsApiLimit) {
            result.suggestedChunkCount = (int) Math.ceil(
                result.fileSizeBytes / (20.0 * 1024 * 1024)  // 20MB chunks for safety
            );
        }

        return result;
    }

    private static String detectFormat(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".wav")) return "wav";
        if (name.endsWith(".mp3")) return "mp3";
        if (name.endsWith(".m4a") || name.endsWith(".aac")) return "m4a";
        if (name.endsWith(".ogg")) return "ogg";
        if (name.endsWith(".flac")) return "flac";
        return "unknown";
    }

    private static Float estimateDuration(File file) {
        // For WAV: calculate from header
        // For others: use ffprobe if available, else estimate from bitrate
        // ...
    }

    private static boolean checkFfmpegAvailable() {
        try {
            Process p = Runtime.getRuntime().exec("ffmpeg -version");
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
```

### 2. Audio Chunking Strategies

#### Strategy A: Native WAV Splitting (No FFmpeg)

```java
public class WavChunker {

    /**
     * Splits a WAV file into chunks of specified duration.
     * Uses Java's AudioSystem - no external dependencies.
     */
    public static List<File> splitWavFile(File wavFile, int chunkDurationSeconds)
            throws Exception {

        List<File> chunks = new ArrayList<>();
        AudioInputStream sourceStream = AudioSystem.getAudioInputStream(wavFile);
        AudioFormat format = sourceStream.getFormat();

        long framesPerChunk = (long) (format.getSampleRate() * chunkDurationSeconds);
        long totalFrames = sourceStream.getFrameLength();
        int numChunks = (int) Math.ceil((double) totalFrames / framesPerChunk);

        for (int i = 0; i < numChunks; i++) {
            long framesToRead = Math.min(framesPerChunk, totalFrames - (i * framesPerChunk));

            // Create chunk file
            File chunkFile = File.createTempFile("chunk_" + i + "_", ".wav");
            chunkFile.deleteOnExit();

            // Read frames for this chunk
            byte[] buffer = new byte[(int) (framesToRead * format.getFrameSize())];
            sourceStream.read(buffer);

            // Write chunk
            ByteArrayInputStream bais = new ByteArrayInputStream(buffer);
            AudioInputStream chunkStream = new AudioInputStream(
                bais, format, framesToRead
            );
            AudioSystem.write(chunkStream, AudioFileFormat.Type.WAVE, chunkFile);

            chunks.add(chunkFile);
        }

        sourceStream.close();
        return chunks;
    }
}
```

#### Strategy B: FFmpeg-Based Splitting (All Formats)

```java
public class FfmpegChunker {

    /**
     * Splits any audio file into chunks using FFmpeg.
     * More versatile but requires FFmpeg installation.
     */
    public static List<File> splitAudioFile(File audioFile, int chunkDurationSeconds)
            throws Exception {

        List<File> chunks = new ArrayList<>();

        // Get total duration
        float totalDuration = getAudioDuration(audioFile);
        int numChunks = (int) Math.ceil(totalDuration / chunkDurationSeconds);

        for (int i = 0; i < numChunks; i++) {
            int startTime = i * chunkDurationSeconds;

            File chunkFile = File.createTempFile("chunk_" + i + "_", ".wav");
            chunkFile.deleteOnExit();

            // FFmpeg command to extract chunk and convert to WAV
            ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y",
                "-i", audioFile.getAbsolutePath(),
                "-ss", String.valueOf(startTime),
                "-t", String.valueOf(chunkDurationSeconds),
                "-acodec", "pcm_s16le",
                "-ar", "16000",
                "-ac", "1",
                chunkFile.getAbsolutePath()
            );

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0 && chunkFile.exists() && chunkFile.length() > 0) {
                chunks.add(chunkFile);
            }
        }

        return chunks;
    }

    private static float getAudioDuration(File file) throws Exception {
        // Use ffprobe to get duration
        ProcessBuilder pb = new ProcessBuilder(
            "ffprobe", "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            file.getAbsolutePath()
        );

        Process process = pb.start();
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream())
        );
        String line = reader.readLine();
        process.waitFor();

        return Float.parseFloat(line);
    }
}
```

#### Strategy C: FFmpeg Compression (Reduce Size Without Splitting)

```java
public class FfmpegCompressor {

    /**
     * Compresses audio to reduce file size below API limit.
     * Converts to mono 16kHz MP3 - optimal for speech transcription.
     */
    public static File compressForTranscription(File audioFile) throws Exception {
        File compressedFile = File.createTempFile("compressed_", ".mp3");
        compressedFile.deleteOnExit();

        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg", "-y",
            "-i", audioFile.getAbsolutePath(),
            "-ar", "16000",      // 16kHz sample rate (sufficient for speech)
            "-ac", "1",          // Mono
            "-b:a", "32k",       // 32kbps bitrate (very small, still intelligible)
            "-acodec", "libmp3lame",
            compressedFile.getAbsolutePath()
        );

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode == 0 && compressedFile.length() < 25 * 1024 * 1024) {
            return compressedFile;
        }

        // If still too large, need to split
        return null;
    }
}
```

### 3. Chunked Transcription Worker

```java
/**
 * Worker for transcribing large audio files in chunks.
 * Shows progress and merges results.
 */
private class ChunkedTranscriptionWorker extends SwingWorker<String, String> {
    private final List<File> chunks;
    private final int totalChunks;

    public ChunkedTranscriptionWorker(List<File> chunks) {
        this.chunks = chunks;
        this.totalChunks = chunks.size();
    }

    @Override
    protected String doInBackground() throws Exception {
        StringBuilder fullTranscript = new StringBuilder();
        ConsoleLogger console = ConsoleLogger.getInstance();

        for (int i = 0; i < chunks.size(); i++) {
            File chunk = chunks.get(i);

            // Update progress
            publish(String.format("Transcribing chunk %d of %d...", i + 1, totalChunks));
            console.log(String.format("Processing chunk %d/%d (%s)",
                i + 1, totalChunks, formatFileSize(chunk.length())));

            // Transcribe chunk
            String chunkTranscript = transcribeChunk(chunk);

            if (chunkTranscript != null && !chunkTranscript.trim().isEmpty()) {
                if (fullTranscript.length() > 0) {
                    fullTranscript.append(" ");  // Space between chunks
                }
                fullTranscript.append(chunkTranscript.trim());
            }

            // Update progress percentage
            setProgress((i + 1) * 100 / totalChunks);
        }

        return fullTranscript.toString();
    }

    @Override
    protected void process(List<String> messages) {
        // Update UI with progress messages
        for (String message : messages) {
            recordButton.setText(message);
        }
    }

    @Override
    protected void done() {
        try {
            String transcript = get();
            transcriptionTextArea.setText(transcript);
            ConsoleLogger.getInstance().logSuccess(
                String.format("Chunked transcription complete (%d chunks merged)", totalChunks)
            );
            // ... rest of completion handling
        } catch (Exception e) {
            // Handle error
        }
    }

    private String transcribeChunk(File chunk) {
        // Use existing transcription client based on settings
        String server = configManager.getWhisperServer();
        if ("OpenAI".equals(server)) {
            return whisperClient.transcribe(chunk);
        } else if ("Faster-Whisper".equals(server)) {
            return fasterWhisperTranscribeClient.transcribe(chunk);
        }
        // ...
    }
}
```

### 4. Integration Points

**RecorderForm.java - Modify `handleDroppedAudioFile()`:**

```java
private void handleDroppedAudioFile(File file) {
    // NEW: Pre-flight analysis
    AudioFileAnalyzer.AnalysisResult analysis = AudioFileAnalyzer.analyze(file);

    if (analysis.exceedsApiLimit && "OpenAI".equals(configManager.getWhisperServer())) {
        // Show options dialog
        showLargeFileOptionsDialog(file, analysis);
        return;
    }

    // Existing flow for files under limit
    // ...
}

private void showLargeFileOptionsDialog(File file, AudioFileAnalyzer.AnalysisResult analysis) {
    LargeFileOptionsDialog dialog = new LargeFileOptionsDialog(
        SwingUtilities.getWindowAncestor(this),
        file,
        analysis
    );

    dialog.setVisible(true);

    if (dialog.wasCancelled()) {
        return;
    }

    switch (dialog.getSelectedOption()) {
        case SPLIT_AND_TRANSCRIBE:
            handleChunkedTranscription(file, analysis);
            break;
        case COMPRESS_FIRST:
            handleCompressAndTranscribe(file);
            break;
        case USE_LOCAL_WHISPER:
            // Switch to Faster-Whisper and proceed
            handleLocalWhisperTranscription(file);
            break;
    }
}
```

---

## Implementation Phases

### Phase 1: Pre-Flight Analysis
1. Create `AudioFileAnalyzer` class
2. Add file size check before transcription
3. Show simple warning for oversized files
4. Log analysis results to console

### Phase 2: Native WAV Splitting
1. Create `WavChunker` class
2. Test with WAV files only
3. Implement `ChunkedTranscriptionWorker`
4. Merge transcriptions with proper spacing

### Phase 3: FFmpeg Integration
1. Create `FfmpegChunker` class
2. Add FFmpeg availability detection
3. Support M4A/MP3/OGG splitting
4. Add compression option

### Phase 4: Options Dialog UI
1. Create `LargeFileOptionsDialog`
2. Show file analysis results
3. Present available options based on system capabilities
4. Remember user preference option

### Phase 5: Progress & Polish
1. Add progress bar for chunked transcription
2. Show chunk-by-chunk progress in console
3. Handle errors gracefully (partial results)
4. Add "Abort" capability for long transcriptions

---

## Edge Cases & Considerations

### Chunk Boundary Handling
- **Problem:** Splitting mid-word causes transcription errors
- **Solution:** Use overlap (e.g., 2 seconds) at boundaries, deduplicate in merge
- **Alternative:** Let model handle - slight repetition is acceptable

### Memory Management
- WAV files are large in memory
- Stream chunks rather than loading entire file
- Delete temp files promptly

### Network Failures
- If one chunk fails, retry 2-3 times
- Option to skip failed chunk and continue
- Save partial results

### Progress Feedback
- Show which chunk is being processed
- Estimated time remaining
- Allow cancellation

### Format Detection
- Don't rely solely on extension
- Check file magic bytes for actual format
- Handle misnamed files gracefully

---

## Testing Checklist

### Pre-Flight Analysis
- [ ] Correctly identifies file size
- [ ] Detects format from extension
- [ ] Estimates duration for WAV files
- [ ] Checks FFmpeg availability
- [ ] Shows warning for large files

### Native WAV Splitting
- [ ] Splits 100MB WAV into ~5 chunks
- [ ] Each chunk is valid WAV file
- [ ] No audio data lost at boundaries
- [ ] Temp files cleaned up

### FFmpeg Splitting
- [ ] Works with MP3 files
- [ ] Works with M4A files
- [ ] Works with OGG files
- [ ] Handles files without FFmpeg gracefully

### Chunked Transcription
- [ ] All chunks transcribed successfully
- [ ] Results merged in correct order
- [ ] Progress updates shown
- [ ] Can cancel mid-process
- [ ] Handles API errors gracefully

### Options Dialog
- [ ] Shows correct file info
- [ ] Disables unavailable options
- [ ] Remembers preference
- [ ] Cancel works correctly

---

## File Size Reference

**To Help Users Understand Limits:**

| Duration | WAV (CD Quality) | MP3 (128k) | MP3 (32k mono) |
|----------|------------------|------------|----------------|
| 1 min    | 10 MB           | 1 MB       | 240 KB         |
| 10 min   | 100 MB          | 10 MB      | 2.4 MB         |
| 30 min   | 300 MB          | 30 MB      | 7.2 MB         |
| 1 hour   | 600 MB          | 60 MB      | 14.4 MB        |
| 2 hours  | 1.2 GB          | 120 MB     | 28.8 MB        |

**OpenAI 25MB limit ≈:**
- 2.5 min uncompressed WAV
- 25 min MP3 at 128kbps
- 1h 45min MP3 at 32kbps mono

---

## Dependencies

### Required
- Java AudioSystem (built-in) - for WAV handling

### Optional (Enhanced Functionality)
- FFmpeg - for MP3/M4A/OGG support and compression
- FFprobe - for accurate duration detection

### Detection Code
```java
public static boolean isFfmpegAvailable() {
    try {
        Process p = new ProcessBuilder("ffmpeg", "-version")
            .redirectErrorStream(true)
            .start();
        return p.waitFor() == 0;
    } catch (Exception e) {
        return false;
    }
}
```

---

## Success Criteria

Feature is complete when:

- [ ] Large files (>25MB) detected before API call
- [ ] User sees clear options for handling large files
- [ ] WAV files can be split without FFmpeg
- [ ] MP3/M4A files can be split with FFmpeg
- [ ] Compression option available when FFmpeg present
- [ ] Chunked transcription shows progress
- [ ] Results merged seamlessly
- [ ] Partial failure doesn't lose all progress
- [ ] User can cancel long operations
- [ ] Console shows detailed progress
- [ ] No regressions to normal-sized file handling

---

## Estimated Complexity

**Time Estimate:** 6-8 hours

**Breakdown:**
- Phase 1 (Pre-flight analysis): 1 hour
- Phase 2 (WAV splitting): 2 hours
- Phase 3 (FFmpeg integration): 2 hours
- Phase 4 (Options dialog): 1.5 hours
- Phase 5 (Progress & polish): 1.5 hours

**Difficulty:** Medium-High
- Audio stream handling requires care
- Multiple code paths based on capabilities
- Progress reporting adds complexity

---

## Handover Notes

**Previous Session:** `claude/manual-pipeline-runner-spec-01QHA2EN2MGkJaxYWgdYcToE`
**Date:** 2025-11-28

**What Was Completed This Session:**
- Manual pipeline runner with history tracking
- Silence threshold description fix
- Apply Settings button with feedback

**Key Files:**
- `RecorderForm.java` - Main UI, drag & drop handling at line 412
- `SilenceRemover.java` - Example of audio processing in Java
- `FasterWhisperTranscribeClient.java` - Example API client

**Testing Command:**
```bash
mvn clean package && java -jar target/Audiorecorder-1.0-SNAPSHOT-jar-with-dependencies.jar
```

**Good Luck!**
