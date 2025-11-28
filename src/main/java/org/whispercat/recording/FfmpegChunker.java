package org.whispercat.recording;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispercat.ConsoleLogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Splits audio files into chunks using FFmpeg.
 * Supports all audio formats that FFmpeg can handle (MP3, M4A, OGG, FLAC, etc.).
 */
public class FfmpegChunker {

    private static final Logger logger = LogManager.getLogger(FfmpegChunker.class);

    // Default chunk duration: 20 minutes (safe for 25MB limit with most formats)
    public static final int DEFAULT_CHUNK_DURATION_SECONDS = 20 * 60;

    // Overlap between chunks to avoid mid-word splits (2 seconds)
    public static final int CHUNK_OVERLAP_SECONDS = 2;

    // FFmpeg process timeout (5 minutes per chunk should be plenty)
    private static final int FFMPEG_TIMEOUT_SECONDS = 300;

    /**
     * Result of a chunking operation.
     */
    public static class ChunkResult {
        public final List<File> chunks;
        public final int totalChunks;
        public final float totalDurationSeconds;
        public final boolean success;
        public final String errorMessage;

        private ChunkResult(List<File> chunks, float totalDurationSeconds, String errorMessage) {
            this.chunks = chunks != null ? chunks : new ArrayList<>();
            this.totalChunks = this.chunks.size();
            this.totalDurationSeconds = totalDurationSeconds;
            this.success = errorMessage == null;
            this.errorMessage = errorMessage;
        }

        public static ChunkResult success(List<File> chunks, float totalDurationSeconds) {
            return new ChunkResult(chunks, totalDurationSeconds, null);
        }

        public static ChunkResult failure(String errorMessage) {
            return new ChunkResult(null, 0, errorMessage);
        }
    }

    /**
     * Callback interface for progress updates during chunking.
     */
    public interface ChunkingProgressCallback {
        /**
         * Called when a chunk is completed.
         * @param chunkNumber Current chunk number (1-based)
         * @param totalChunks Total number of chunks
         * @param chunkFile The created chunk file
         */
        void onChunkComplete(int chunkNumber, int totalChunks, File chunkFile);

        /**
         * Called when chunking is cancelled.
         */
        void onCancelled();
    }

    // Cancellation flag
    private static volatile boolean cancelled = false;

    /**
     * Cancels any ongoing chunking operation.
     */
    public static void cancel() {
        cancelled = true;
    }

    /**
     * Resets the cancellation flag.
     */
    public static void resetCancellation() {
        cancelled = false;
    }

    /**
     * Splits any audio file into chunks using FFmpeg.
     * Converts chunks to WAV format for maximum compatibility with transcription APIs.
     *
     * @param audioFile The audio file to split
     * @param chunkDurationSeconds Duration of each chunk in seconds
     * @param callback Optional callback for progress updates
     * @return ChunkResult containing the list of chunk files
     */
    public static ChunkResult splitAudioFile(File audioFile, int chunkDurationSeconds,
                                              ChunkingProgressCallback callback) {
        ConsoleLogger console = ConsoleLogger.getInstance();
        cancelled = false;

        try {
            console.log("Analyzing audio file with FFprobe: " + audioFile.getName());

            // Get total duration using ffprobe
            Float totalDuration = getAudioDuration(audioFile);
            if (totalDuration == null) {
                return ChunkResult.failure("Could not determine audio duration. Is FFprobe installed?");
            }

            console.log(String.format("Total duration: %.1f seconds (%.1f minutes)",
                totalDuration, totalDuration / 60.0));

            // Calculate number of chunks
            int numChunks = (int) Math.ceil(totalDuration / chunkDurationSeconds);
            console.log(String.format("Will create %d chunks of ~%d seconds each", numChunks, chunkDurationSeconds));

            List<File> chunks = new ArrayList<>();

            for (int i = 0; i < numChunks; i++) {
                if (cancelled) {
                    console.log("Chunking cancelled by user");
                    cleanupChunks(chunks);
                    if (callback != null) {
                        callback.onCancelled();
                    }
                    return ChunkResult.failure("Chunking cancelled by user");
                }

                // Calculate start time with overlap (except for first chunk)
                int startTime = i * chunkDurationSeconds;
                if (i > 0) {
                    startTime = Math.max(0, startTime - CHUNK_OVERLAP_SECONDS);
                }

                // Calculate duration for this chunk
                int duration = chunkDurationSeconds + (i > 0 ? CHUNK_OVERLAP_SECONDS : 0);

                // Create chunk file
                String baseName = audioFile.getName().replaceFirst("\\.[^.]+$", "");
                File chunkFile = File.createTempFile(
                    String.format("chunk_%02d_%s_", i + 1, baseName), ".wav"
                );
                chunkFile.deleteOnExit();

                console.log(String.format("Creating chunk %d/%d (start: %ds, duration: %ds)...",
                    i + 1, numChunks, startTime, duration));

                // FFmpeg command to extract chunk and convert to WAV
                ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-i", audioFile.getAbsolutePath(),
                    "-ss", String.valueOf(startTime),
                    "-t", String.valueOf(duration),
                    "-acodec", "pcm_s16le",  // 16-bit PCM
                    "-ar", "16000",           // 16kHz sample rate (good for speech)
                    "-ac", "1",               // Mono
                    chunkFile.getAbsolutePath()
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
                    return ChunkResult.failure("FFmpeg timed out processing chunk " + (i + 1));
                }

                int exitCode = process.exitValue();
                if (exitCode != 0 || !chunkFile.exists() || chunkFile.length() == 0) {
                    logger.error("FFmpeg failed for chunk {}. Exit code: {}. Output: {}",
                        i + 1, exitCode, output);
                    return ChunkResult.failure("FFmpeg failed to create chunk " + (i + 1) +
                        ". Exit code: " + exitCode);
                }

                chunks.add(chunkFile);
                console.log(String.format("  Created chunk %d/%d: %.2f MB",
                    i + 1, numChunks, chunkFile.length() / (1024.0 * 1024.0)));

                if (callback != null) {
                    callback.onChunkComplete(i + 1, numChunks, chunkFile);
                }
            }

            console.logSuccess(String.format("Successfully split into %d chunks", chunks.size()));
            return ChunkResult.success(chunks, totalDuration);

        } catch (Exception e) {
            logger.error("Error during FFmpeg chunking", e);
            return ChunkResult.failure("Error during chunking: " + e.getMessage());
        }
    }

    /**
     * Splits an audio file using default chunk duration.
     */
    public static ChunkResult splitAudioFile(File audioFile, int chunkDurationSeconds) {
        return splitAudioFile(audioFile, chunkDurationSeconds, null);
    }

    /**
     * Splits an audio file using default chunk duration and no callback.
     */
    public static ChunkResult splitAudioFile(File audioFile) {
        return splitAudioFile(audioFile, DEFAULT_CHUNK_DURATION_SECONDS, null);
    }

    /**
     * Gets the duration of an audio file using ffprobe.
     *
     * @param file The audio file
     * @return Duration in seconds, or null if it couldn't be determined
     */
    public static Float getAudioDuration(File file) {
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
            process.waitFor(30, TimeUnit.SECONDS);

            if (line != null && !line.isEmpty()) {
                return Float.parseFloat(line.trim());
            }
        } catch (Exception e) {
            logger.error("Could not get duration from ffprobe", e);
        }
        return null;
    }

    /**
     * Splits an audio file to target a specific chunk file size.
     * Calculates duration based on the file's bitrate.
     *
     * @param audioFile The audio file to split
     * @param targetChunkSizeBytes Target size for each chunk in bytes
     * @return ChunkResult containing the list of chunk files
     */
    public static ChunkResult splitBySize(File audioFile, long targetChunkSizeBytes) {
        Float duration = getAudioDuration(audioFile);
        if (duration == null) {
            return ChunkResult.failure("Could not determine audio duration");
        }

        // Calculate average bitrate
        float avgBytesPerSecond = audioFile.length() / duration;

        // Calculate chunk duration to achieve target size
        int chunkDurationSeconds = (int) (targetChunkSizeBytes / avgBytesPerSecond);

        // Ensure minimum of 60 seconds and maximum of 30 minutes
        chunkDurationSeconds = Math.max(60, Math.min(1800, chunkDurationSeconds));

        return splitAudioFile(audioFile, chunkDurationSeconds);
    }

    /**
     * Cleans up temporary chunk files.
     */
    public static void cleanupChunks(List<File> chunks) {
        if (chunks == null) return;

        for (File chunk : chunks) {
            try {
                if (chunk.exists() && chunk.delete()) {
                    logger.debug("Deleted temp chunk: " + chunk.getName());
                }
            } catch (Exception e) {
                logger.warn("Could not delete temp chunk: " + chunk.getName(), e);
            }
        }
    }
}
