package org.whispercat.recording;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispercat.ConsoleLogger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Analyzes audio files before transcription to detect large files and provide options.
 * Performs pre-flight checks for file size, format, duration, and system capabilities.
 */
public class AudioFileAnalyzer {

    private static final Logger logger = LogManager.getLogger(AudioFileAnalyzer.class);

    // OpenAI Whisper API limit is 25MB, we use 24MB for safety margin
    public static final long API_SIZE_LIMIT = 24 * 1024 * 1024;

    // Target chunk size for splitting (20MB to leave room for format overhead)
    public static final long TARGET_CHUNK_SIZE = 20 * 1024 * 1024;

    /**
     * Result of audio file analysis containing all relevant information
     * for determining how to handle large files.
     */
    public static class AnalysisResult {
        public final File file;
        public final long fileSizeBytes;
        public final String format;  // wav, mp3, m4a, ogg, flac, unknown
        public final Float estimatedDurationSeconds;  // null if unknown
        public final boolean exceedsApiLimit;
        public final boolean ffmpegAvailable;
        public final boolean ffprobeAvailable;
        public final boolean canSplitNatively;  // true for WAV only
        public final int suggestedChunkCount;
        public final List<LargeFileOption> availableOptions;

        public AnalysisResult(File file, long fileSizeBytes, String format,
                             Float estimatedDurationSeconds, boolean exceedsApiLimit,
                             boolean ffmpegAvailable, boolean ffprobeAvailable,
                             boolean canSplitNatively, int suggestedChunkCount,
                             List<LargeFileOption> availableOptions) {
            this.file = file;
            this.fileSizeBytes = fileSizeBytes;
            this.format = format;
            this.estimatedDurationSeconds = estimatedDurationSeconds;
            this.exceedsApiLimit = exceedsApiLimit;
            this.ffmpegAvailable = ffmpegAvailable;
            this.ffprobeAvailable = ffprobeAvailable;
            this.canSplitNatively = canSplitNatively;
            this.suggestedChunkCount = suggestedChunkCount;
            this.availableOptions = availableOptions;
        }

        /**
         * Returns a human-readable file size string.
         */
        public String getFormattedFileSize() {
            if (fileSizeBytes >= 1024 * 1024 * 1024) {
                return String.format("%.2f GB", fileSizeBytes / (1024.0 * 1024.0 * 1024.0));
            } else if (fileSizeBytes >= 1024 * 1024) {
                return String.format("%.1f MB", fileSizeBytes / (1024.0 * 1024.0));
            } else if (fileSizeBytes >= 1024) {
                return String.format("%.1f KB", fileSizeBytes / 1024.0);
            } else {
                return fileSizeBytes + " bytes";
            }
        }

        /**
         * Returns a human-readable duration string.
         */
        public String getFormattedDuration() {
            if (estimatedDurationSeconds == null) {
                return "Unknown";
            }
            int totalSeconds = estimatedDurationSeconds.intValue();
            int hours = totalSeconds / 3600;
            int minutes = (totalSeconds % 3600) / 60;
            int seconds = totalSeconds % 60;

            if (hours > 0) {
                return String.format("%dh %dm %ds", hours, minutes, seconds);
            } else if (minutes > 0) {
                return String.format("%dm %ds", minutes, seconds);
            } else {
                return String.format("%ds", seconds);
            }
        }

        /**
         * Returns estimated transcription time based on chunk count.
         * Assumes ~2-3 minutes per chunk for OpenAI API.
         */
        public String getEstimatedTranscriptionTime() {
            if (suggestedChunkCount <= 1) {
                return "1-2 minutes";
            }
            int minMinutes = suggestedChunkCount * 2;
            int maxMinutes = suggestedChunkCount * 3;
            return String.format("%d-%d minutes", minMinutes, maxMinutes);
        }
    }

    /**
     * Options available for handling large files.
     */
    public enum LargeFileOption {
        SPLIT_AND_TRANSCRIBE("Split into chunks and transcribe sequentially"),
        COMPRESS_FIRST("Compress with FFmpeg first, then transcribe"),
        USE_LOCAL_WHISPER("Use Faster-Whisper (local) - no size limit"),
        CANCEL("Cancel");

        private final String description;

        LargeFileOption(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Analyzes an audio file and returns comprehensive information about it.
     * This is the main entry point for pre-flight analysis.
     *
     * @param audioFile The audio file to analyze
     * @return AnalysisResult containing all analysis information
     */
    public static AnalysisResult analyze(File audioFile) {
        ConsoleLogger console = ConsoleLogger.getInstance();

        long fileSizeBytes = audioFile.length();
        String format = detectFormat(audioFile);
        Float estimatedDuration = estimateDuration(audioFile, format);
        boolean exceedsLimit = fileSizeBytes > API_SIZE_LIMIT;
        boolean ffmpegAvailable = checkFfmpegAvailable();
        boolean ffprobeAvailable = checkFfprobeAvailable();
        boolean canSplitNatively = "wav".equals(format);

        int suggestedChunks = 0;
        if (exceedsLimit) {
            suggestedChunks = (int) Math.ceil((double) fileSizeBytes / TARGET_CHUNK_SIZE);
        }

        List<LargeFileOption> options = determineAvailableOptions(
            format, ffmpegAvailable, canSplitNatively, exceedsLimit
        );

        AnalysisResult result = new AnalysisResult(
            audioFile, fileSizeBytes, format, estimatedDuration, exceedsLimit,
            ffmpegAvailable, ffprobeAvailable, canSplitNatively, suggestedChunks, options
        );

        // Log analysis results
        logAnalysisResults(result, console);

        return result;
    }

    /**
     * Detects the audio format from file extension and magic bytes.
     */
    public static String detectFormat(File file) {
        String name = file.getName().toLowerCase();

        // First check by extension
        if (name.endsWith(".wav")) return "wav";
        if (name.endsWith(".mp3")) return "mp3";
        if (name.endsWith(".m4a") || name.endsWith(".aac")) return "m4a";
        if (name.endsWith(".ogg") || name.endsWith(".oga")) return "ogg";
        if (name.endsWith(".flac")) return "flac";
        if (name.endsWith(".webm")) return "webm";

        // Try to detect by magic bytes if extension is unclear
        String magicFormat = detectFormatByMagicBytes(file);
        if (magicFormat != null) {
            return magicFormat;
        }

        return "unknown";
    }

    /**
     * Detects audio format by reading file header (magic bytes).
     */
    private static String detectFormatByMagicBytes(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] header = new byte[12];
            if (raf.read(header) >= 12) {
                // RIFF....WAVE = WAV
                if (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F' &&
                    header[8] == 'W' && header[9] == 'A' && header[10] == 'V' && header[11] == 'E') {
                    return "wav";
                }
                // ID3 or 0xFF 0xFB = MP3
                if ((header[0] == 'I' && header[1] == 'D' && header[2] == '3') ||
                    (header[0] == (byte) 0xFF && (header[1] & 0xE0) == 0xE0)) {
                    return "mp3";
                }
                // ftyp = M4A/MP4
                if (header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p') {
                    return "m4a";
                }
                // OggS = OGG
                if (header[0] == 'O' && header[1] == 'g' && header[2] == 'g' && header[3] == 'S') {
                    return "ogg";
                }
                // fLaC = FLAC
                if (header[0] == 'f' && header[1] == 'L' && header[2] == 'a' && header[3] == 'C') {
                    return "flac";
                }
            }
        } catch (Exception e) {
            logger.debug("Could not read file magic bytes", e);
        }
        return null;
    }

    /**
     * Estimates audio duration based on file format.
     * For WAV: calculates exactly from header
     * For others: uses ffprobe if available, otherwise estimates from file size and typical bitrates
     */
    public static Float estimateDuration(File file, String format) {
        // For WAV files, calculate duration from header
        if ("wav".equals(format)) {
            return estimateWavDuration(file);
        }

        // Try ffprobe for accurate duration
        Float ffprobeDuration = getDurationFromFfprobe(file);
        if (ffprobeDuration != null) {
            return ffprobeDuration;
        }

        // Estimate based on typical bitrates for compressed formats
        return estimateDurationFromSize(file.length(), format);
    }

    /**
     * Calculates exact duration for WAV files from header.
     */
    private static Float estimateWavDuration(File file) {
        try {
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(file);
            AudioFormat format = audioStream.getFormat();
            long frames = audioStream.getFrameLength();
            float frameRate = format.getFrameRate();
            audioStream.close();

            if (frames > 0 && frameRate > 0) {
                return frames / frameRate;
            }
        } catch (Exception e) {
            logger.debug("Could not read WAV duration from header", e);
        }

        // Fallback: estimate from file size assuming 16-bit 44.1kHz stereo
        // (10 MB per minute roughly)
        return file.length() / (10.0f * 1024 * 1024) * 60;
    }

    /**
     * Gets duration using ffprobe command.
     */
    private static Float getDurationFromFfprobe(File file) {
        try {
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
            int exitCode = process.waitFor();

            if (exitCode == 0 && line != null && !line.isEmpty()) {
                return Float.parseFloat(line.trim());
            }
        } catch (Exception e) {
            logger.debug("Could not get duration from ffprobe", e);
        }
        return null;
    }

    /**
     * Estimates duration from file size based on typical bitrates.
     */
    private static Float estimateDurationFromSize(long fileSizeBytes, String format) {
        // Typical bitrates (bits per second)
        int typicalBitrate;
        switch (format) {
            case "mp3":
                typicalBitrate = 128000; // 128 kbps
                break;
            case "m4a":
                typicalBitrate = 128000; // AAC 128 kbps
                break;
            case "ogg":
                typicalBitrate = 112000; // Vorbis ~112 kbps average
                break;
            case "flac":
                typicalBitrate = 800000; // ~800 kbps for FLAC
                break;
            default:
                return null; // Can't estimate for unknown formats
        }

        // duration = file size in bits / bitrate
        return (fileSizeBytes * 8.0f) / typicalBitrate;
    }

    /**
     * Checks if FFmpeg is available on the system.
     */
    public static boolean checkFfmpegAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if FFprobe is available on the system.
     */
    public static boolean checkFfprobeAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffprobe", "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Determines which options are available for handling a large file.
     */
    private static List<LargeFileOption> determineAvailableOptions(
            String format, boolean ffmpegAvailable, boolean canSplitNatively, boolean exceedsLimit) {

        List<LargeFileOption> options = new ArrayList<>();

        if (!exceedsLimit) {
            // File is within limits, no special handling needed
            return options;
        }

        // Option 1: Split and transcribe (always available for WAV, or if FFmpeg is available)
        if (canSplitNatively || ffmpegAvailable) {
            options.add(LargeFileOption.SPLIT_AND_TRANSCRIBE);
        }

        // Option 2: Compress first (only if FFmpeg is available)
        if (ffmpegAvailable) {
            options.add(LargeFileOption.COMPRESS_FIRST);
        }

        // Option 3: Use local Whisper (always available as an option)
        options.add(LargeFileOption.USE_LOCAL_WHISPER);

        // Option 4: Cancel is always available
        options.add(LargeFileOption.CANCEL);

        return options;
    }

    /**
     * Logs analysis results to the console for user visibility.
     */
    private static void logAnalysisResults(AnalysisResult result, ConsoleLogger console) {
        console.separator();
        console.log("Audio file analysis:");
        console.log("  File: " + result.file.getName());
        console.log("  Size: " + result.getFormattedFileSize());
        console.log("  Format: " + result.format.toUpperCase());

        if (result.estimatedDurationSeconds != null) {
            console.log("  Duration: " + result.getFormattedDuration());
        }

        if (result.exceedsApiLimit) {
            console.log("  Status: EXCEEDS 25MB API LIMIT");
            console.log("  Suggested chunks: " + result.suggestedChunkCount);
            console.log("  FFmpeg available: " + (result.ffmpegAvailable ? "Yes" : "No"));
            console.log("  Native WAV split: " + (result.canSplitNatively ? "Yes" : "No"));
        } else {
            console.log("  Status: Within API limits");
        }
    }
}
