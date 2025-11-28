package org.whispercat.recording;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispercat.ConsoleLogger;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits WAV files into chunks using Java's AudioSystem.
 * No external dependencies required - works with pure Java.
 */
public class WavChunker {

    private static final Logger logger = LogManager.getLogger(WavChunker.class);

    // Default chunk duration: 20 minutes (leaves room under 25MB for most bitrates)
    public static final int DEFAULT_CHUNK_DURATION_SECONDS = 20 * 60;

    // Overlap between chunks to avoid mid-word splits (2 seconds)
    public static final int CHUNK_OVERLAP_SECONDS = 2;

    /**
     * Result of a chunking operation.
     */
    public static class ChunkResult {
        public final List<File> chunks;
        public final int totalChunks;
        public final long totalDurationSeconds;
        public final boolean success;
        public final String errorMessage;

        private ChunkResult(List<File> chunks, long totalDurationSeconds, String errorMessage) {
            this.chunks = chunks != null ? chunks : new ArrayList<>();
            this.totalChunks = this.chunks.size();
            this.totalDurationSeconds = totalDurationSeconds;
            this.success = errorMessage == null;
            this.errorMessage = errorMessage;
        }

        public static ChunkResult success(List<File> chunks, long totalDurationSeconds) {
            return new ChunkResult(chunks, totalDurationSeconds, null);
        }

        public static ChunkResult failure(String errorMessage) {
            return new ChunkResult(null, 0, errorMessage);
        }
    }

    /**
     * Splits a WAV file into chunks of specified duration.
     * Uses Java's AudioSystem - no external dependencies required.
     *
     * @param wavFile The WAV file to split
     * @param chunkDurationSeconds Duration of each chunk in seconds
     * @return ChunkResult containing the list of chunk files
     */
    public static ChunkResult splitWavFile(File wavFile, int chunkDurationSeconds) {
        ConsoleLogger console = ConsoleLogger.getInstance();

        try {
            console.log("Opening WAV file for chunking: " + wavFile.getName());

            // Read the WAV file
            AudioInputStream sourceStream = AudioSystem.getAudioInputStream(wavFile);
            AudioFormat format = sourceStream.getFormat();

            // Validate format
            if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED &&
                format.getEncoding() != AudioFormat.Encoding.PCM_UNSIGNED) {
                sourceStream.close();
                return ChunkResult.failure("Unsupported WAV encoding: " + format.getEncoding() +
                    ". Only PCM WAV files are supported for native chunking.");
            }

            float sampleRate = format.getSampleRate();
            int frameSize = format.getFrameSize();
            long totalFrames = sourceStream.getFrameLength();

            if (totalFrames <= 0) {
                sourceStream.close();
                return ChunkResult.failure("Could not determine WAV file length");
            }

            long totalDurationSeconds = (long) (totalFrames / sampleRate);
            console.log(String.format("WAV file: %.1f seconds, %.1f kHz, %d-bit, %d channels",
                totalDurationSeconds, sampleRate / 1000, format.getSampleSizeInBits(), format.getChannels()));

            // Calculate frames per chunk
            long framesPerChunk = (long) (sampleRate * chunkDurationSeconds);
            long overlapFrames = (long) (sampleRate * CHUNK_OVERLAP_SECONDS);
            int numChunks = (int) Math.ceil((double) totalFrames / framesPerChunk);

            console.log(String.format("Splitting into %d chunks of ~%d seconds each",
                numChunks, chunkDurationSeconds));

            List<File> chunks = new ArrayList<>();
            long currentFrame = 0;
            int chunkIndex = 0;

            // Read the entire file into memory for easier chunking
            byte[] allAudioData = sourceStream.readAllBytes();
            sourceStream.close();

            while (currentFrame < totalFrames) {
                // Calculate frames to read for this chunk (include overlap for all but first chunk)
                long startFrame = currentFrame;
                if (chunkIndex > 0 && currentFrame >= overlapFrames) {
                    startFrame = currentFrame - overlapFrames;
                }

                long endFrame = Math.min(currentFrame + framesPerChunk, totalFrames);
                long framesToWrite = endFrame - startFrame;

                // Extract audio data for this chunk
                int startByte = (int) (startFrame * frameSize);
                int endByte = (int) (endFrame * frameSize);
                int byteLength = endByte - startByte;

                if (startByte >= allAudioData.length) {
                    break;
                }
                if (endByte > allAudioData.length) {
                    endByte = allAudioData.length;
                    byteLength = endByte - startByte;
                    framesToWrite = byteLength / frameSize;
                }

                byte[] chunkData = new byte[byteLength];
                System.arraycopy(allAudioData, startByte, chunkData, 0, byteLength);

                // Create chunk file
                String baseName = wavFile.getName().replaceFirst("\\.[^.]+$", "");
                File chunkFile = File.createTempFile(
                    String.format("chunk_%02d_%s_", chunkIndex + 1, baseName), ".wav"
                );
                chunkFile.deleteOnExit();

                // Write chunk as WAV file
                ByteArrayInputStream bais = new ByteArrayInputStream(chunkData);
                AudioInputStream chunkStream = new AudioInputStream(bais, format, framesToWrite);
                AudioSystem.write(chunkStream, AudioFileFormat.Type.WAVE, chunkFile);
                chunkStream.close();

                chunks.add(chunkFile);
                console.log(String.format("  Created chunk %d/%d: %s (%.1f MB)",
                    chunkIndex + 1, numChunks, chunkFile.getName(),
                    chunkFile.length() / (1024.0 * 1024.0)));

                currentFrame = endFrame;
                chunkIndex++;
            }

            console.logSuccess(String.format("Successfully split into %d chunks", chunks.size()));
            return ChunkResult.success(chunks, totalDurationSeconds);

        } catch (UnsupportedAudioFileException e) {
            logger.error("Unsupported audio format", e);
            return ChunkResult.failure("Unsupported WAV format: " + e.getMessage());
        } catch (IOException e) {
            logger.error("Error reading/writing WAV file", e);
            return ChunkResult.failure("Error processing WAV file: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during WAV chunking", e);
            return ChunkResult.failure("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Splits a WAV file using default chunk duration.
     */
    public static ChunkResult splitWavFile(File wavFile) {
        return splitWavFile(wavFile, DEFAULT_CHUNK_DURATION_SECONDS);
    }

    /**
     * Splits a WAV file into chunks of a target size rather than duration.
     * Calculates appropriate duration based on file's bitrate.
     *
     * @param wavFile The WAV file to split
     * @param targetChunkSizeBytes Target size for each chunk in bytes
     * @return ChunkResult containing the list of chunk files
     */
    public static ChunkResult splitWavFileBySize(File wavFile, long targetChunkSizeBytes) {
        try {
            AudioInputStream stream = AudioSystem.getAudioInputStream(wavFile);
            AudioFormat format = stream.getFormat();

            // Calculate bytes per second
            float bytesPerSecond = format.getFrameRate() * format.getFrameSize();

            // Calculate duration to achieve target size
            int chunkDurationSeconds = (int) (targetChunkSizeBytes / bytesPerSecond);

            // Ensure minimum chunk duration of 60 seconds
            chunkDurationSeconds = Math.max(60, chunkDurationSeconds);

            stream.close();

            return splitWavFile(wavFile, chunkDurationSeconds);
        } catch (Exception e) {
            logger.error("Error calculating chunk duration from size", e);
            // Fall back to default duration
            return splitWavFile(wavFile, DEFAULT_CHUNK_DURATION_SECONDS);
        }
    }

    /**
     * Cleans up temporary chunk files.
     * Call this after transcription is complete.
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
