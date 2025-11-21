package org.whispercat.postprocessing;

import org.whispercat.ConfigManager;
import org.whispercat.Notificationmanager;
import org.whispercat.ToastNotification;
import org.whispercat.postprocessing.clients.OpenWebUIProcessClient;
import org.whispercat.recording.OpenAIClient;

import java.io.IOException;

public class PostProcessingService {

    private static final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger(PostProcessingService.class);

    // OpenAIClient instance used to make synchronous calls to the API.
    private OpenAIClient openAIClient;
    private OpenWebUIProcessClient openWebUIClient;

    /**
     * Constructs the PostProcessingService with the given ConfigManager.
     * The OpenAIClient is initialized here.
     *
     * @param configManager The ConfigManager that contains configuration settings.
     */
    public PostProcessingService(ConfigManager configManager) {
        this.openAIClient = new OpenAIClient(configManager);
        this.openWebUIClient = new OpenWebUIProcessClient(configManager);
    }

    /**
     * Applies the defined post-processing steps sequentially.
     *
     * @param originalText       The initial transcribed text.
     * @param postProcessingData The configuration for post-processing.
     * @return The processed text after all steps.
     */
    public String applyPostProcessing(String originalText, PostProcessingData postProcessingData) {
        String processedText = originalText;
        int totalSteps = postProcessingData.steps.size();
        int enabledStepCount = 0;
        int currentStep = 0;

        // Count enabled steps
        for (ProcessingStepData step : postProcessingData.steps) {
            if (step.enabled) {
                enabledStepCount++;
            }
        }

        // Show initial notification
        if (enabledStepCount > 0) {
            Notificationmanager.getInstance().showNotification(ToastNotification.Type.INFO,
                    "Starting post-processing (" + enabledStepCount + " steps)...");
        }

        // Iterate over all defined steps.
        for (ProcessingStepData step : postProcessingData.steps) {
            // Skip disabled steps
            if (!step.enabled) {
                logger.info("Skipping disabled processing step: " + step.type);
                continue;
            }

            currentStep++;
            String stepDescription = getStepDescription(step, currentStep, enabledStepCount);

            // Show notification for current step
            Notificationmanager.getInstance().showNotification(ToastNotification.Type.INFO,
                    stepDescription);

            if ("Prompt".equalsIgnoreCase(step.type)) {
                // Use OpenAIClient for a synchronous call.
                processedText = performPromptProcessing(processedText, step);
            } else if ("Text Replacement".equalsIgnoreCase(step.type)) {
                // Replace text based on configuration.
                processedText = processedText.replace(step.textToReplace, step.replacementText);
            } else {
                // Log unknown step type.
                System.out.println("Unknown post-processing step type: " + step.type);
            }
        }

        // Show completion notification
        if (enabledStepCount > 0) {
            Notificationmanager.getInstance().showNotification(ToastNotification.Type.SUCCESS,
                    "Post-processing completed!");
        }

        return processedText;
    }

    /**
     * Generates a descriptive message for the current processing step.
     */
    private String getStepDescription(ProcessingStepData step, int currentStep, int totalSteps) {
        StringBuilder sb = new StringBuilder();
        sb.append("Step ").append(currentStep).append("/").append(totalSteps).append(": ");

        if ("Prompt".equalsIgnoreCase(step.type)) {
            sb.append("Processing with ").append(step.provider);
            if (step.model != null && !step.model.isEmpty()) {
                sb.append(" (").append(step.model).append(")");
            }
        } else if ("Text Replacement".equalsIgnoreCase(step.type)) {
            sb.append("Replacing text");
        } else {
            sb.append(step.type);
        }

        return sb.toString();
    }

    /**
     * Synchronously processes the text using OpenAI API via a prompt.
     * The processing is executed step-by-step. After receiving the API response,
     * the result is used as input for the next processing step.
     *
     * @param inputText The input text to process.
     * @param step      The processing configuration.
     * @return The processed text from the OpenAI response.
     */
    private String performPromptProcessing(String inputText, ProcessingStepData step) {

        logger.info("Pre-processing input: " + step.userPrompt);
        logger.info("Transcript: " + inputText);
        // Combine the user prompt with the input text.
        String fullUserPrompt = step.userPrompt.replaceAll("\\{\\{input}}", inputText);
        logger.info("Post-processing input: " + fullUserPrompt);
        try {
            // Synchronous call using the provided OpenAIClient.
            if(step.provider.equalsIgnoreCase("OpenAI")){
                logger.info("Processing using OpenAI API.");
                String result = openAIClient.processText(step.systemPrompt, fullUserPrompt, step.model);
                return result;
            } else if(step.provider.equalsIgnoreCase("Open WebUI")){
                logger.info("Processing using Open WebUI.");
                String result = openWebUIClient.processText(step.systemPrompt, fullUserPrompt, step.model);
                return result;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return inputText;
    }
}