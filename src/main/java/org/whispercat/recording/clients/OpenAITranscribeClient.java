package org.whispercat.recording.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispercat.ConfigManager;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class OpenAITranscribeClient {
    private static final Logger logger = LogManager.getLogger(OpenAITranscribeClient.class);
    private static final String API_URL = "https://api.openai.com/v1/audio/transcriptions";
    private static final long MAX_FILE_SIZE = 24 * 1024 * 1024; // 24 MB (leaving buffer under 25MB limit)
    private static final int CONNECTION_TIMEOUT = 30000; // 30 seconds
    private static final int SOCKET_TIMEOUT = 600000; // 10 minutes for large file processing
    private final ConfigManager configManager;

    public OpenAITranscribeClient(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Compresses the audio file to MP3 format using ffmpeg to reduce file size.
     * This is much more effective than downsampling - can reduce size by 10x or more.
     * Creates a temporary compressed file.
     *
     * @param originalFile The original audio file
     * @return The compressed MP3 file, or null if compression fails
     */
    private File compressAudioToMp3(File originalFile) {
        try {
            logger.info("Compressing audio file to MP3: {} (size: {} MB)",
                originalFile.getName(), originalFile.length() / (1024.0 * 1024.0));

            // Create temporary MP3 file
            File mp3File = File.createTempFile("whispercat_compressed_", ".mp3");
            mp3File.deleteOnExit();

            // Use ffmpeg to convert to MP3 with good compression
            // -y = overwrite output file
            // -i = input file
            // -codec:a libmp3lame = use LAME MP3 encoder
            // -q:a 4 = VBR quality (0-9, where 0 is best, 9 is worst; 4 is good for speech ~140kbps)
            // -ac 1 = mono audio (speech doesn't need stereo, halves file size)
            // -ar 16000 = 16kHz sample rate (good for speech recognition)
            ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-y",
                "-i", originalFile.getAbsolutePath(),
                "-codec:a", "libmp3lame",
                "-q:a", "4",
                "-ac", "1",
                "-ar", "16000",
                mp3File.getAbsolutePath()
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read output to prevent blocking
            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0 && mp3File.exists() && mp3File.length() > 0) {
                logger.info("Successfully compressed to MP3: {} (size: {} MB, compression ratio: {:.1f}x)",
                    mp3File.getName(),
                    mp3File.length() / (1024.0 * 1024.0),
                    (double) originalFile.length() / mp3File.length());
                return mp3File;
            } else {
                logger.error("ffmpeg conversion failed with exit code: {}. Output: {}", exitCode, output);
                return null;
            }
        } catch (Exception e) {
            logger.error("Failed to compress audio file to MP3", e);
            return null;
        }
    }

    /**
     * Legacy compression method using downsampling.
     * Kept as fallback if ffmpeg is not available.
     *
     * @param originalFile The original audio file
     * @return The compressed audio file, or the original if compression fails
     */
    private File compressAudioFileByDownsampling(File originalFile) {
        try {
            logger.info("Compressing audio file by downsampling: {} (size: {} MB)",
                originalFile.getName(), originalFile.length() / (1024.0 * 1024.0));

            // Read the original audio file
            AudioInputStream originalStream = AudioSystem.getAudioInputStream(originalFile);
            AudioFormat originalFormat = originalStream.getFormat();

            // Create a new format with lower sample rate (16kHz is good for speech)
            float newSampleRate = 16000.0f;
            AudioFormat targetFormat = new AudioFormat(
                originalFormat.getEncoding(),
                newSampleRate,
                originalFormat.getSampleSizeInBits(),
                originalFormat.getChannels(),
                originalFormat.getFrameSize(),
                newSampleRate,
                originalFormat.isBigEndian()
            );

            // Convert to the new format
            AudioInputStream convertedStream = AudioSystem.getAudioInputStream(targetFormat, originalStream);

            // Create a temporary file for the compressed audio
            File compressedFile = File.createTempFile("whispercat_compressed_", ".wav");
            compressedFile.deleteOnExit();

            // Write the converted audio to the temporary file
            AudioSystem.write(convertedStream, AudioFileFormat.Type.WAVE, compressedFile);

            // Close streams
            convertedStream.close();
            originalStream.close();

            logger.info("Compressed audio file created: {} (size: {} MB)",
                compressedFile.getName(), compressedFile.length() / (1024.0 * 1024.0));

            return compressedFile;
        } catch (Exception e) {
            logger.error("Failed to compress audio file, using original", e);
            return originalFile;
        }
    }

    /**
     * Compresses the audio file to reduce size before uploading to OpenAI.
     * First tries MP3 compression via ffmpeg (10x+ compression).
     * Falls back to downsampling if ffmpeg is not available.
     *
     * @param originalFile The original audio file
     * @return The compressed audio file, or the original if compression fails
     */
    private File compressAudioFile(File originalFile) {
        // Try MP3 compression first (much better compression)
        File mp3File = compressAudioToMp3(originalFile);
        if (mp3File != null && mp3File.length() < originalFile.length()) {
            // Check if MP3 is still too large
            if (mp3File.length() > MAX_FILE_SIZE) {
                logger.warn("MP3 file still exceeds size limit ({} MB). File may be too long for OpenAI.",
                    mp3File.length() / (1024.0 * 1024.0));
            }
            return mp3File;
        }

        // Fall back to downsampling if ffmpeg failed or is not available
        logger.warn("MP3 compression failed or not available. Falling back to downsampling.");
        return compressAudioFileByDownsampling(originalFile);
    }

    public String transcribe(File audioFile) throws IOException {
        // Check if file size exceeds limit and compress if necessary
        File fileToTranscribe = audioFile;
        if (audioFile.length() > MAX_FILE_SIZE) {
            logger.warn("Audio file size ({} MB) exceeds OpenAI limit (25 MB). Compressing...",
                audioFile.length() / (1024.0 * 1024.0));
            fileToTranscribe = compressAudioFile(audioFile);
        }

        // Configure timeouts to prevent indefinite hanging
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(CONNECTION_TIMEOUT)
            .setSocketTimeout(SOCKET_TIMEOUT)
            .setConnectionRequestTimeout(CONNECTION_TIMEOUT)
            .build();

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {
            HttpPost httpPost = new HttpPost(API_URL);
            httpPost.setHeader("Authorization", "Bearer " + configManager.getApiKey());

            // Determine content type based on file extension
            String fileName = fileToTranscribe.getName().toLowerCase();
            String contentType = "audio/wav"; // default
            if (fileName.endsWith(".mp3")) {
                contentType = "audio/mpeg";
            } else if (fileName.endsWith(".m4a")) {
                contentType = "audio/mp4";
            } else if (fileName.endsWith(".ogg")) {
                contentType = "audio/ogg";
            } else if (fileName.endsWith(".flac")) {
                contentType = "audio/flac";
            }

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("file", fileToTranscribe, ContentType.create(contentType), fileToTranscribe.getName());
            builder.addTextBody("model", "whisper-1");

            HttpEntity multipart = builder.build();
            httpPost.setEntity(multipart);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                HttpEntity responseEntity = response.getEntity();
                String responseString = new String(responseEntity.getContent().readAllBytes(), StandardCharsets.UTF_8);

                if (statusCode != 200) {
                    logger.error("OpenAI API returned status code: {}. Response: {}", statusCode, responseString);

                    // Try to parse as JSON to get error message
                    try {
                        ObjectMapper objectMapper = new ObjectMapper();
                        JsonNode jsonNode = objectMapper.readTree(responseString);
                        String errorMessage = jsonNode.path("error").path("message").asText("Unknown error");
                        throw new IOException("Error from OpenAI API (HTTP " + statusCode + "): " + errorMessage);
                    } catch (Exception jsonException) {
                        // Response is not valid JSON, use raw response
                        logger.error("Failed to parse error response as JSON", jsonException);
                        // Truncate very long responses
                        String truncatedResponse = responseString.length() > 500
                            ? responseString.substring(0, 500) + "..."
                            : responseString;
                        throw new IOException("Error from OpenAI API (HTTP " + statusCode + "): " + truncatedResponse);
                    }
                }

                // Parse successful response
                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode jsonNode = objectMapper.readTree(responseString);
                    String transcription = jsonNode.path("text").asText();
                    if (transcription == null || transcription.isEmpty()) {
                        logger.warn("OpenAI returned empty transcription");
                        throw new IOException("OpenAI returned empty transcription");
                    }
                    return transcription;
                } catch (Exception jsonException) {
                    logger.error("Failed to parse successful response as JSON. Response: {}", responseString, jsonException);
                    throw new IOException("Failed to parse OpenAI response: " + jsonException.getMessage());
                }
            }
        }
    }
}