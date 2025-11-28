package org.whispercat.recording;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispercat.ConsoleLogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Compresses audio files using FFmpeg to reduce file size.
 * Optimizes for speech recognition (mono, 16kHz, low bitrate).
 */
public class FfmpegCompressor {

    private static final Logger logger = LogManager.getLogger(FfmpegCompressor.class);

    // Target file size (under API limit with buffer)
    public static final long TARGET_SIZE = AudioFileAnalyzer.API_SIZE_LIMIT;

    // FFmpeg process timeout (5 minutes should be enough for most files)
    private static final int FFMPEG_TIMEOUT_SECONDS = 300;

    /**
     * Compression presets for different quality/size trade-offs.
     */
    public enum CompressionPreset {
        /** High quality: 64kbps mono - good for long meetings */
        HIGH_QUALITY("64k", "High Quality (64kbps)"),

        /** Balanced: 48kbps mono - recommended for most use cases */
        BALANCED("48k", "Balanced (48kbps)"),

        /** Maximum compression: 32kbps mono - still intelligible for speech */
        MAXIMUM("32k", "Maximum Compression (32kbps)"),

        /** Ultra compression: 24kbps mono - acceptable for transcription */
        ULTRA("24k", "Ultra Compression (24kbps)");

        private final String bitrate;
        private final String description;

        CompressionPreset(String bitrate, String description) {
            this.bitrate = bitrate;
            this.description = description;
        }

        public String getBitrate() {
            return bitrate;
        }

        public String getDescription() {
            return description;
        }

        /**
         * Estimates output file size for a given duration.
         * @param durationSeconds Audio duration in seconds
         * @return Estimated file size in bytes
         */
        public long estimateOutputSize(float durationSeconds) {
            // Parse bitrate (e.g., "32k" -> 32000)
            int bitrateValue = Integer.parseInt(bitrate.replace("k", "")) * 1000;
            // Add ~10% overhead for MP3 container
            return (long) (durationSeconds * bitrateValue / 8 * 1.1);
        }
    }

    /**
     * Result of a compression operation.
     */
    public static class CompressionResult {
        public final File compressedFile;
        public final long originalSize;
        public final long compressedSize;
        public final float compressionRatio;
        public final boolean success;
        public final boolean withinLimit;
        public final String errorMessage;

        private CompressionResult(File compressedFile, long originalSize, long compressedSize,
                                   String errorMessage) {
            this.compressedFile = compressedFile;
            this.originalSize = originalSize;
            this.compressedSize = compressedSize;
            this.compressionRatio = compressedSize > 0 ? (float) originalSize / compressedSize : 0;
            this.success = errorMessage == null;
            this.withinLimit = compressedSize > 0 && compressedSize <= TARGET_SIZE;
            this.errorMessage = errorMessage;
        }

        public static CompressionResult success(File compressedFile, long originalSize, long compressedSize) {
            return new CompressionResult(compressedFile, originalSize, compressedSize, null);
        }

        public static CompressionResult failure(String errorMessage) {
            return new CompressionResult(null, 0, 0, errorMessage);
        }

        /**
         * Returns a human-readable summary of the compression.
         */
        public String getSummary() {
            if (!success) {
                return "Compression failed: " + errorMessage;
            }
            return String.format("Compressed %.1f MB -> %.1f MB (%.1fx reduction)%s",
                originalSize / (1024.0 * 1024.0),
                compressedSize / (1024.0 * 1024.0),
                compressionRatio,
                withinLimit ? "" : " - still exceeds limit!");
        }
    }

    /**
     * Compresses an audio file for transcription using the recommended preset.
     * Automatically selects a preset based on file size and duration.
     *
     * @param audioFile The audio file to compress
     * @return CompressionResult containing the compressed file
     */
    public static CompressionResult compress(File audioFile) {
        // Estimate duration and choose appropriate preset
        Float duration = FfmpegChunker.getAudioDuration(audioFile);
        if (duration == null) {
            // Default to balanced if we can't get duration
            return compress(audioFile, CompressionPreset.BALANCED);
        }

        // Choose preset based on file size and duration
        CompressionPreset preset = chooseOptimalPreset(audioFile.length(), duration);
        return compress(audioFile, preset);
    }

    /**
     * Compresses an audio file using the specified preset.
     *
     * @param audioFile The audio file to compress
     * @param preset Compression preset to use
     * @return CompressionResult containing the compressed file
     */
    public static CompressionResult compress(File audioFile, CompressionPreset preset) {
        ConsoleLogger console = ConsoleLogger.getInstance();
        long originalSize = audioFile.length();

        try {
            console.log(String.format("Compressing audio file: %s (%.2f MB)",
                audioFile.getName(), originalSize / (1024.0 * 1024.0)));
            console.log("Compression preset: " + preset.getDescription());

            // Create temporary file for compressed output
            File compressedFile = File.createTempFile("whispercat_compressed_", ".mp3");
            compressedFile.deleteOnExit();

            // FFmpeg compression command optimized for speech
            ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y",
                "-i", audioFile.getAbsolutePath(),
                "-ar", "16000",           // 16kHz sample rate (sufficient for speech)
                "-ac", "1",               // Mono (speech doesn't need stereo)
                "-b:a", preset.getBitrate(), // Bitrate from preset
                "-acodec", "libmp3lame",  // MP3 encoder (widely supported)
                compressedFile.getAbsolutePath()
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read output to prevent blocking
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(FFMPEG_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return CompressionResult.failure("FFmpeg timed out during compression");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                logger.error("FFmpeg compression failed. Exit code: {}. Output: {}",
                    exitCode, output);
                return CompressionResult.failure("FFmpeg compression failed with exit code: " + exitCode);
            }

            if (!compressedFile.exists() || compressedFile.length() == 0) {
                return CompressionResult.failure("FFmpeg produced empty output file");
            }

            long compressedSize = compressedFile.length();
            CompressionResult result = CompressionResult.success(compressedFile, originalSize, compressedSize);

            console.log(result.getSummary());

            if (!result.withinLimit) {
                console.log("Compressed file still exceeds API limit - will need to split");
            } else {
                console.logSuccess("Compression successful - file is within API limit");
            }

            return result;

        } catch (Exception e) {
            logger.error("Error during audio compression", e);
            return CompressionResult.failure("Compression error: " + e.getMessage());
        }
    }

    /**
     * Chooses the optimal compression preset based on file characteristics.
     * Aims to get the file under the API limit with the highest quality possible.
     */
    private static CompressionPreset chooseOptimalPreset(long fileSizeBytes, float durationSeconds) {
        // Try presets from highest quality to lowest until we find one that fits
        for (CompressionPreset preset : CompressionPreset.values()) {
            long estimatedSize = preset.estimateOutputSize(durationSeconds);
            if (estimatedSize <= TARGET_SIZE) {
                return preset;
            }
        }

        // File is too large even with maximum compression
        // Return ultra compression anyway - caller will need to split
        return CompressionPreset.ULTRA;
    }

    /**
     * Estimates whether compression alone can get a file under the API limit.
     *
     * @param fileSizeBytes Original file size in bytes
     * @param durationSeconds Audio duration in seconds
     * @return true if compression is likely sufficient, false if splitting is needed
     */
    public static boolean canFitWithCompression(long fileSizeBytes, float durationSeconds) {
        // Estimate with ultra compression (most aggressive)
        long ultraEstimate = CompressionPreset.ULTRA.estimateOutputSize(durationSeconds);
        return ultraEstimate <= TARGET_SIZE;
    }

    /**
     * Estimates the compression ratio that can be achieved for a given format.
     *
     * @param format Audio format (wav, mp3, m4a, etc.)
     * @param targetBitrate Target bitrate in kbps
     * @return Estimated compression ratio
     */
    public static float estimateCompressionRatio(String format, int targetBitrate) {
        // Approximate source bitrates for different formats
        int sourceBitrate;
        switch (format.toLowerCase()) {
            case "wav":
                // 16-bit stereo 44.1kHz = ~1411 kbps
                sourceBitrate = 1411;
                break;
            case "flac":
                // FLAC typically ~800 kbps
                sourceBitrate = 800;
                break;
            case "mp3":
                // MP3 typically 128-320 kbps
                sourceBitrate = 192;
                break;
            case "m4a":
            case "aac":
                // AAC typically 128-256 kbps
                sourceBitrate = 160;
                break;
            case "ogg":
                // Vorbis typically 96-160 kbps
                sourceBitrate = 128;
                break;
            default:
                sourceBitrate = 256; // Assume moderate bitrate for unknown formats
        }

        return (float) sourceBitrate / targetBitrate;
    }
}
