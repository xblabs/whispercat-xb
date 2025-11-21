package org.whispercat.recording.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
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
    private final ConfigManager configManager;

    public OpenAITranscribeClient(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Compresses the audio file by downsampling to reduce file size.
     * Creates a temporary compressed file.
     *
     * @param originalFile The original audio file
     * @return The compressed audio file, or the original if compression fails
     */
    private File compressAudioFile(File originalFile) {
        try {
            logger.info("Compressing audio file {} (size: {} MB)",
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

    public String transcribe(File audioFile) throws IOException {
        // Check if file size exceeds limit and compress if necessary
        File fileToTranscribe = audioFile;
        if (audioFile.length() > MAX_FILE_SIZE) {
            logger.warn("Audio file size ({} MB) exceeds OpenAI limit (25 MB). Compressing...",
                audioFile.length() / (1024.0 * 1024.0));
            fileToTranscribe = compressAudioFile(audioFile);
        }
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(API_URL);
            httpPost.setHeader("Authorization", "Bearer " + configManager.getApiKey());

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("file", fileToTranscribe, ContentType.create("audio/wav"), fileToTranscribe.getName());
            builder.addTextBody("model", "whisper-1");

            HttpEntity multipart = builder.build();
            httpPost.setEntity(multipart);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                HttpEntity responseEntity = response.getEntity();
                String responseString = new String(responseEntity.getContent().readAllBytes(), StandardCharsets.UTF_8);

                if (statusCode != 200) {
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode jsonNode = objectMapper.readTree(responseString);
                    String errorMessage = jsonNode.path("error").path("message").asText();
                    logger.error("Error from OpenAI API: {}", errorMessage);
                    throw new IOException("Error from OpenAI API: " + errorMessage);
                }

                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(responseString);
                return jsonNode.path("text").asText();
            }
        }
    }
}