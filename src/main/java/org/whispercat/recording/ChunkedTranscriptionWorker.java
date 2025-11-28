package org.whispercat.recording;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispercat.ConfigManager;
import org.whispercat.ConsoleLogger;
import org.whispercat.recording.clients.FasterWhisperTranscribeClient;
import org.whispercat.recording.clients.OpenAITranscribeClient;
import org.whispercat.recording.clients.OpenWebUITranscribeClient;

import javax.swing.*;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SwingWorker for transcribing large audio files that have been split into chunks.
 * Shows progress and merges results from all chunks into a single transcript.
 */
public class ChunkedTranscriptionWorker extends SwingWorker<String, ChunkedTranscriptionWorker.Progress> {

    private static final Logger logger = LogManager.getLogger(ChunkedTranscriptionWorker.class);

    /**
     * Progress update sent to the UI.
     */
    public static class Progress {
        public final int currentChunk;
        public final int totalChunks;
        public final String message;
        public final boolean isError;

        public Progress(int currentChunk, int totalChunks, String message, boolean isError) {
            this.currentChunk = currentChunk;
            this.totalChunks = totalChunks;
            this.message = message;
            this.isError = isError;
        }

        public int getPercentComplete() {
            return totalChunks > 0 ? (currentChunk * 100) / totalChunks : 0;
        }
    }

    /**
     * Callback interface for progress and completion updates.
     */
    public interface Callback {
        /**
         * Called when progress is updated.
         */
        void onProgress(Progress progress);

        /**
         * Called when transcription is complete.
         */
        void onComplete(String fullTranscript);

        /**
         * Called when an error occurs.
         */
        void onError(String errorMessage);

        /**
         * Called when the operation is cancelled.
         */
        void onCancelled();
    }

    private final List<File> chunks;
    private final ConfigManager configManager;
    private final OpenAITranscribeClient openAIClient;
    private final FasterWhisperTranscribeClient fasterWhisperClient;
    private final OpenWebUITranscribeClient openWebUIClient;
    private final Callback callback;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    // Retry configuration
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 2000;

    /**
     * Creates a new chunked transcription worker.
     *
     * @param chunks List of audio chunk files to transcribe
     * @param configManager Configuration manager for settings
     * @param callback Callback for progress and completion updates
     */
    public ChunkedTranscriptionWorker(List<File> chunks, ConfigManager configManager, Callback callback) {
        this.chunks = chunks;
        this.configManager = configManager;
        this.callback = callback;

        this.openAIClient = new OpenAITranscribeClient(configManager);
        this.fasterWhisperClient = new FasterWhisperTranscribeClient(configManager);
        this.openWebUIClient = new OpenWebUITranscribeClient(configManager);
    }

    /**
     * Cancels the transcription operation.
     */
    public void cancelTranscription() {
        cancelled.set(true);
        cancel(false);
    }

    @Override
    protected String doInBackground() throws Exception {
        ConsoleLogger console = ConsoleLogger.getInstance();
        StringBuilder fullTranscript = new StringBuilder();
        int totalChunks = chunks.size();

        console.separator();
        console.log("Starting chunked transcription of " + totalChunks + " chunks");
        console.log("Using transcription server: " + configManager.getWhisperServer());

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < chunks.size(); i++) {
            if (cancelled.get() || isCancelled()) {
                console.log("Transcription cancelled by user");
                publish(new Progress(i, totalChunks, "Cancelled", false));
                return null;
            }

            File chunk = chunks.get(i);
            int chunkNum = i + 1;

            publish(new Progress(i, totalChunks,
                String.format("Transcribing chunk %d of %d...", chunkNum, totalChunks), false));

            console.log(String.format("Processing chunk %d/%d: %s (%.2f MB)",
                chunkNum, totalChunks, chunk.getName(), chunk.length() / (1024.0 * 1024.0)));

            // Transcribe chunk with retries
            String chunkTranscript = transcribeWithRetries(chunk, chunkNum);

            if (chunkTranscript == null) {
                // Check if cancelled during transcription
                if (cancelled.get()) {
                    return null;
                }

                // Failed after retries - decide whether to continue or fail
                console.logError(String.format("Failed to transcribe chunk %d after %d retries",
                    chunkNum, MAX_RETRIES));

                // Continue with other chunks, mark this one as failed
                console.log("Continuing with remaining chunks...");
                fullTranscript.append("[TRANSCRIPTION FAILED FOR CHUNK ")
                    .append(chunkNum)
                    .append("] ");
            } else if (!chunkTranscript.trim().isEmpty()) {
                // Successfully transcribed
                if (fullTranscript.length() > 0) {
                    fullTranscript.append(" ");  // Space between chunks
                }
                fullTranscript.append(chunkTranscript.trim());

                console.log(String.format("  Chunk %d transcribed: %d characters",
                    chunkNum, chunkTranscript.length()));
            }

            // Update progress
            setProgress((chunkNum * 100) / totalChunks);
        }

        long elapsedTime = System.currentTimeMillis() - startTime;
        console.logSuccess(String.format("Chunked transcription complete (%d chunks in %.1f seconds)",
            totalChunks, elapsedTime / 1000.0));
        console.log(String.format("Total transcript length: %d characters", fullTranscript.length()));

        return fullTranscript.toString();
    }

    /**
     * Transcribes a single chunk with retry logic.
     */
    private String transcribeWithRetries(File chunk, int chunkNum) {
        ConsoleLogger console = ConsoleLogger.getInstance();

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            if (cancelled.get()) {
                return null;
            }

            try {
                String result = transcribeChunk(chunk);
                if (result != null) {
                    return result;
                }

                // Null result but no exception - might be empty response
                console.log(String.format("  Chunk %d attempt %d: empty response, retrying...",
                    chunkNum, attempt));

            } catch (Exception e) {
                logger.error("Error transcribing chunk " + chunkNum + " (attempt " + attempt + ")", e);
                console.logError(String.format("  Chunk %d attempt %d failed: %s",
                    chunkNum, attempt, e.getMessage()));
            }

            if (attempt < MAX_RETRIES) {
                // Wait before retry
                try {
                    Thread.sleep(RETRY_DELAY_MS * attempt);  // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }

        return null;  // All retries failed
    }

    /**
     * Transcribes a single chunk using the configured transcription service.
     */
    private String transcribeChunk(File chunk) throws Exception {
        String server = configManager.getWhisperServer();

        switch (server) {
            case "OpenAI":
                return openAIClient.transcribe(chunk);

            case "Faster-Whisper":
                return fasterWhisperClient.transcribe(chunk);

            case "Open WebUI":
                return openWebUIClient.transcribeAudio(chunk);

            default:
                throw new IllegalStateException("Unknown Whisper server: " + server);
        }
    }

    @Override
    protected void process(List<Progress> updates) {
        if (callback != null && !updates.isEmpty()) {
            // Process the most recent update
            Progress latest = updates.get(updates.size() - 1);
            callback.onProgress(latest);
        }
    }

    @Override
    protected void done() {
        // Clean up temporary chunk files
        cleanupChunks();

        if (callback == null) return;

        if (cancelled.get() || isCancelled()) {
            callback.onCancelled();
            return;
        }

        try {
            String result = get();
            if (result != null) {
                callback.onComplete(result);
            } else {
                callback.onError("Transcription returned no results");
            }
        } catch (Exception e) {
            logger.error("Error in chunked transcription", e);
            callback.onError("Transcription failed: " + e.getMessage());
        }
    }

    /**
     * Cleans up temporary chunk files.
     */
    private void cleanupChunks() {
        for (File chunk : chunks) {
            try {
                if (chunk.exists()) {
                    boolean deleted = chunk.delete();
                    if (!deleted) {
                        chunk.deleteOnExit();
                    }
                }
            } catch (Exception e) {
                logger.warn("Could not delete temp chunk: " + chunk.getName(), e);
            }
        }
    }

    /**
     * Formats file size in human-readable form.
     */
    public static String formatFileSize(long bytes) {
        if (bytes >= 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else if (bytes >= 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return bytes + " bytes";
        }
    }
}
