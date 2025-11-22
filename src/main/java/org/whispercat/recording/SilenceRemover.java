package org.whispercat.recording;

import org.whispercat.ConsoleLogger;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Removes silence from audio recordings to reduce file size and transcription costs.
 * Uses RMS (Root Mean Square) amplitude analysis to detect silent regions.
 */
public class SilenceRemover {

    private static final org.apache.logging.log4j.Logger logger =
        org.apache.logging.log4j.LogManager.getLogger(SilenceRemover.class);

    /**
     * Represents a silent region in the audio.
     */
    private static class SilenceRegion {
        long startFrame;
        long endFrame;

        SilenceRegion(long startFrame, long endFrame) {
            this.startFrame = startFrame;
            this.endFrame = endFrame;
        }

        long getDurationFrames() {
            return endFrame - startFrame;
        }
    }

    /**
     * Removes silence from an audio file.
     *
     * @param originalFile The original audio file
     * @param silenceThresholdRMS RMS threshold for silence detection (0.0-1.0, typically 0.01 = -40dB)
     * @param minSilenceDurationMs Minimum consecutive duration to consider as silence (milliseconds)
     * @param keepCompressed Whether to keep the compressed file after transcription
     * @return The compressed audio file, or original if no silence detected
     */
    public static File removeSilence(File originalFile, float silenceThresholdRMS,
                                     int minSilenceDurationMs, boolean keepCompressed) {
        ConsoleLogger console = ConsoleLogger.getInstance();
        long startTime = System.currentTimeMillis();

        try {
            console.log("Analyzing audio for silence...");

            // Read audio file
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(originalFile);
            AudioFormat format = audioStream.getFormat();

            // Validate format
            if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
                console.log("Audio format not supported for silence removal (not PCM), skipping");
                audioStream.close();
                return originalFile;
            }

            // Read all audio data into memory
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = audioStream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            audioStream.close();

            byte[] audioData = baos.toByteArray();

            // Calculate original duration
            float sampleRate = format.getSampleRate();
            int frameSize = format.getFrameSize();
            long totalFrames = audioData.length / frameSize;
            float originalDurationSec = totalFrames / sampleRate;

            console.log(String.format("Original duration: %.1fs (%d frames)",
                originalDurationSec, totalFrames));

            // Safety check: ensure original audio is long enough
            if (originalDurationSec < 1.0f) {
                console.log("Original audio too short for silence removal (< 1s), skipping");
                return originalFile;
            }

            // Log detection parameters for transparency
            console.log(String.format("Silence threshold: %.3f RMS | Min duration: %dms",
                silenceThresholdRMS, minSilenceDurationMs));

            // Detect silence regions
            List<SilenceRegion> silences = detectSilence(audioData, format,
                silenceThresholdRMS, minSilenceDurationMs);

            if (silences.isEmpty()) {
                console.log("No significant silence detected");
                return originalFile;
            }

            // Calculate statistics
            long totalSilenceFrames = silences.stream()
                .mapToLong(SilenceRegion::getDurationFrames)
                .sum();
            float totalSilenceSec = totalSilenceFrames / sampleRate;
            float reductionPercent = (totalSilenceSec / originalDurationSec) * 100;

            console.log(String.format("Detected %d silence region(s) (total: %.1fs)",
                silences.size(), totalSilenceSec));
            console.log(String.format("Reduction: %.1f%%", reductionPercent));

            // Safety check: don't remove more than 90% of audio
            if (reductionPercent > 90.0f) {
                console.log("⚠ Silence removal would reduce audio by >90%, skipping");
                console.log("This may indicate overly aggressive settings");
                console.log("Using original audio file");
                return originalFile;
            }

            // Create compressed audio by removing silence
            byte[] compressedData = spliceAudio(audioData, format, silences);
            long compressedFrames = compressedData.length / frameSize;
            float compressedDurationSec = compressedFrames / sampleRate;

            console.log(String.format("Compressed duration: %.1fs (%d frames)",
                compressedDurationSec, compressedFrames));

            // Safety check: ensure compressed audio is at least 0.5 seconds
            // (OpenAI requires 0.1s minimum, we use 0.5s for safety margin)
            if (compressedDurationSec < 0.5f) {
                console.log("⚠ Compressed audio too short (" +
                    String.format("%.2fs", compressedDurationSec) +
                    "), skipping silence removal");
                console.log("Using original audio file");
                return originalFile;
            }

            // Write compressed audio to file
            String compressedFileName = originalFile.getName().replace(".wav", "_nosilence.wav");
            File compressedFile = new File(originalFile.getParent(), compressedFileName);

            AudioInputStream compressedStream = new AudioInputStream(
                new ByteArrayInputStream(compressedData),
                format,
                compressedFrames
            );

            AudioSystem.write(compressedStream, AudioFileFormat.Type.WAVE, compressedFile);
            compressedStream.close();

            long elapsedTime = System.currentTimeMillis() - startTime;
            console.logSuccess("Silence removed: " + compressedFile.getName());
            console.log(String.format("Silence removal took %dms", elapsedTime));

            // Delete compressed file after use if configured
            if (!keepCompressed) {
                compressedFile.deleteOnExit();
            }

            return compressedFile;

        } catch (Exception e) {
            logger.error("Error removing silence from audio", e);
            console.logError("Silence removal failed: " + e.getMessage());
            console.log("Using original audio file");
            return originalFile;
        }
    }

    /**
     * Detects silence regions in audio data using RMS amplitude analysis.
     */
    private static List<SilenceRegion> detectSilence(byte[] audioData, AudioFormat format,
                                                     float silenceThresholdRMS, int minSilenceDurationMs) {
        List<SilenceRegion> silences = new ArrayList<>();

        float sampleRate = format.getSampleRate();
        int frameSize = format.getFrameSize();
        int sampleSizeInBytes = format.getSampleSizeInBits() / 8;
        boolean isBigEndian = format.isBigEndian();

        // Window size: 100ms
        int windowFrames = (int) (sampleRate * 0.1); // 100ms windows
        int windowBytes = windowFrames * frameSize;

        // Minimum silence frames
        long minSilenceFrames = (long) ((minSilenceDurationMs / 1000.0) * sampleRate);

        long silenceStartFrame = -1;

        // Analyze audio in windows
        for (int offset = 0; offset < audioData.length; offset += windowBytes) {
            int length = Math.min(windowBytes, audioData.length - offset);

            // Calculate RMS for this window
            float rms = calculateRMS(audioData, offset, length, sampleSizeInBytes, isBigEndian);

            long currentFrame = offset / frameSize;

            if (rms < silenceThresholdRMS) {
                // Silence detected
                if (silenceStartFrame == -1) {
                    silenceStartFrame = currentFrame;
                }
            } else {
                // Sound detected
                if (silenceStartFrame != -1) {
                    // End of silence region
                    long silenceDuration = currentFrame - silenceStartFrame;
                    if (silenceDuration >= minSilenceFrames) {
                        silences.add(new SilenceRegion(silenceStartFrame, currentFrame));
                    }
                    silenceStartFrame = -1;
                }
            }
        }

        // Handle silence at end of file
        if (silenceStartFrame != -1) {
            long endFrame = audioData.length / frameSize;
            long silenceDuration = endFrame - silenceStartFrame;
            if (silenceDuration >= minSilenceFrames) {
                silences.add(new SilenceRegion(silenceStartFrame, endFrame));
            }
        }

        return silences;
    }

    /**
     * Calculates RMS (Root Mean Square) amplitude for an audio segment.
     * Returns a value between 0.0 (silent) and 1.0 (maximum amplitude).
     */
    private static float calculateRMS(byte[] audioData, int offset, int length,
                                     int sampleSizeInBytes, boolean isBigEndian) {
        double sum = 0.0;
        int sampleCount = 0;

        for (int i = offset; i < offset + length; i += sampleSizeInBytes) {
            if (i + sampleSizeInBytes > audioData.length) break;

            // Read sample value
            int sample = 0;
            if (sampleSizeInBytes == 2) {
                // 16-bit audio
                if (isBigEndian) {
                    sample = (audioData[i] << 8) | (audioData[i + 1] & 0xFF);
                } else {
                    sample = (audioData[i] & 0xFF) | (audioData[i + 1] << 8);
                }
                // Normalize to -1.0 to 1.0
                double normalized = sample / 32768.0;
                sum += normalized * normalized;
                sampleCount++;
            } else if (sampleSizeInBytes == 1) {
                // 8-bit audio
                sample = audioData[i];
                double normalized = (sample - 128) / 128.0;
                sum += normalized * normalized;
                sampleCount++;
            }
        }

        if (sampleCount == 0) return 0.0f;

        return (float) Math.sqrt(sum / sampleCount);
    }

    /**
     * Creates new audio data by removing silence regions.
     */
    private static byte[] spliceAudio(byte[] audioData, AudioFormat format,
                                     List<SilenceRegion> silences) {
        int frameSize = format.getFrameSize();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        long lastEndFrame = 0;

        for (SilenceRegion silence : silences) {
            // Copy audio from last position to start of this silence
            int startByte = (int) (lastEndFrame * frameSize);
            int endByte = (int) (silence.startFrame * frameSize);

            if (endByte > startByte && startByte < audioData.length) {
                int length = Math.min(endByte - startByte, audioData.length - startByte);
                output.write(audioData, startByte, length);
            }

            lastEndFrame = silence.endFrame;
        }

        // Copy remaining audio after last silence
        int startByte = (int) (lastEndFrame * frameSize);
        if (startByte < audioData.length) {
            output.write(audioData, startByte, audioData.length - startByte);
        }

        return output.toByteArray();
    }
}
