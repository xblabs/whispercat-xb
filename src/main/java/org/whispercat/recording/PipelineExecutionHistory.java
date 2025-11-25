package org.whispercat.recording;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tracks pipeline execution history for the current recording session.
 * History is session-scoped (cleared on new recording) and NOT persisted to disk.
 */
public class PipelineExecutionHistory {
    private String recordingId;
    private String originalTranscription;
    private final List<PipelineResult> results = new ArrayList<>();

    /**
     * Represents a single pipeline execution result.
     */
    public static class PipelineResult {
        private final String pipelineUuid;
        private final String pipelineName;
        private final String resultText;
        private final long timestamp;
        private int executionTimeMs;

        public PipelineResult(String uuid, String name, String text) {
            this.pipelineUuid = uuid;
            this.pipelineName = name;
            this.resultText = text;
            this.timestamp = System.currentTimeMillis();
        }

        public PipelineResult(String uuid, String name, String text, int executionTimeMs) {
            this(uuid, name, text);
            this.executionTimeMs = executionTimeMs;
        }

        public String getPipelineUuid() {
            return pipelineUuid;
        }

        public String getPipelineName() {
            return pipelineName;
        }

        public String getResultText() {
            return resultText;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public int getExecutionTimeMs() {
            return executionTimeMs;
        }
    }

    /**
     * Starts a new recording session, clearing all previous history.
     * Call this when a new transcription completes.
     *
     * @param transcription The original transcription text
     */
    public void startNewSession(String transcription) {
        this.recordingId = UUID.randomUUID().toString();
        this.originalTranscription = transcription;
        this.results.clear();
    }

    /**
     * Adds a pipeline execution result to history.
     * Results are added at the beginning (newest first).
     *
     * @param pipelineUuid The pipeline's UUID
     * @param pipelineName The pipeline's display name
     * @param resultText   The processed text result
     */
    public void addResult(String pipelineUuid, String pipelineName, String resultText) {
        results.add(0, new PipelineResult(pipelineUuid, pipelineName, resultText));
    }

    /**
     * Adds a pipeline execution result with execution time.
     *
     * @param pipelineUuid    The pipeline's UUID
     * @param pipelineName    The pipeline's display name
     * @param resultText      The processed text result
     * @param executionTimeMs Time taken to execute in milliseconds
     */
    public void addResult(String pipelineUuid, String pipelineName, String resultText, int executionTimeMs) {
        results.add(0, new PipelineResult(pipelineUuid, pipelineName, resultText, executionTimeMs));
    }

    /**
     * Returns a copy of all results (newest first).
     */
    public List<PipelineResult> getResults() {
        return new ArrayList<>(results);
    }

    /**
     * Returns the number of results in history.
     */
    public int getResultCount() {
        return results.size();
    }

    /**
     * Returns true if there are any results in history.
     */
    public boolean hasResults() {
        return !results.isEmpty();
    }

    /**
     * Returns the original transcription text for this session.
     */
    public String getOriginalTranscription() {
        return originalTranscription;
    }

    /**
     * Returns the recording session ID.
     */
    public String getRecordingId() {
        return recordingId;
    }

    /**
     * Clears all history.
     */
    public void clear() {
        this.recordingId = null;
        this.originalTranscription = null;
        this.results.clear();
    }

    /**
     * Checks if a session is active (has been started with startNewSession).
     */
    public boolean hasActiveSession() {
        return recordingId != null;
    }
}
