package org.whispercat.postprocessing;

import org.whispercat.ConfigManager;
import org.whispercat.ConsoleLogger;
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
    private ConfigManager configManager;

    /**
     * Constructs the PostProcessingService with the given ConfigManager.
     * The OpenAIClient is initialized here.
     *
     * @param configManager The ConfigManager that contains configuration settings.
     */
    public PostProcessingService(ConfigManager configManager) {
        this.configManager = configManager;
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

    /**
     * Executes a pipeline by resolving unit references and applying enabled units in sequence.
     *
     * @param originalText The initial transcribed text.
     * @param pipeline     The pipeline configuration to execute.
     * @return The processed text after all enabled units.
     */
    public String applyPipeline(String originalText, Pipeline pipeline) {
        ConsoleLogger console = ConsoleLogger.getInstance();

        if (!pipeline.enabled) {
            logger.info("Pipeline '{}' is disabled, skipping execution", pipeline.title);
            console.log("Pipeline '" + pipeline.title + "' is disabled, skipping");
            return originalText;
        }

        String processedText = originalText;
        int enabledUnitCount = 0;
        int currentUnit = 0;

        // Count enabled units
        for (PipelineUnitReference ref : pipeline.unitReferences) {
            if (ref.enabled) {
                enabledUnitCount++;
            }
        }

        // Log pipeline start
        console.separator();
        console.log("Starting pipeline: " + pipeline.title);
        console.log("Enabled units: " + enabledUnitCount);
        console.separator();

        // Iterate over unit references
        for (PipelineUnitReference ref : pipeline.unitReferences) {
            // Skip disabled units
            if (!ref.enabled) {
                logger.info("Skipping disabled unit reference in pipeline: {}", ref.unitUuid);
                console.log("Skipping disabled unit: " + ref.unitUuid);
                continue;
            }

            // Resolve the unit from the library
            ProcessingUnit unit = configManager.getProcessingUnitByUuid(ref.unitUuid);
            if (unit == null) {
                logger.warn("Unit with UUID {} not found, skipping", ref.unitUuid);
                console.logError("Unit not found: " + ref.unitUuid);
                continue;
            }

            currentUnit++;

            // Log unit start
            console.logStep(unit.name + " (" + unit.type + ")", currentUnit, enabledUnitCount);

            // Process based on unit type
            if ("Prompt".equalsIgnoreCase(unit.type)) {
                processedText = performPromptProcessingWithUnit(processedText, unit, currentUnit, enabledUnitCount);
            } else if ("Text Replacement".equalsIgnoreCase(unit.type)) {
                console.log("  Replacing: '" + unit.textToReplace + "' → '" + unit.replacementText + "'");
                processedText = processedText.replace(unit.textToReplace, unit.replacementText);
                console.logSuccess("Text replacement completed");
            } else {
                logger.warn("Unknown unit type: {}", unit.type);
                console.logError("Unknown unit type: " + unit.type);
            }
        }

        // Log pipeline completion
        console.separator();
        console.logSuccess("Pipeline completed: " + pipeline.title);
        console.separator();

        return processedText;
    }

    /**
     * Generates a descriptive message for the current processing unit.
     */
    private String getUnitDescription(ProcessingUnit unit, int currentUnit, int totalUnits) {
        StringBuilder sb = new StringBuilder();
        sb.append("Unit ").append(currentUnit).append("/").append(totalUnits).append(": ");
        sb.append(unit.name);

        if ("Prompt".equalsIgnoreCase(unit.type)) {
            sb.append(" (").append(unit.provider);
            if (unit.model != null && !unit.model.isEmpty()) {
                sb.append(" - ").append(unit.model);
            }
            sb.append(")");
        } else if ("Text Replacement".equalsIgnoreCase(unit.type)) {
            sb.append(" (Replace: '").append(unit.textToReplace).append("')");
        }

        return sb.toString();
    }

    /**
     * Synchronously processes the text using a ProcessingUnit's prompt configuration.
     *
     * @param inputText The input text to process.
     * @param unit      The processing unit configuration.
     * @param currentUnit Current unit number.
     * @param enabledUnitCount Total enabled units.
     * @return The processed text from the API response.
     */
    private String performPromptProcessingWithUnit(String inputText, ProcessingUnit unit, int currentUnit, int enabledUnitCount) {
        ConsoleLogger console = ConsoleLogger.getInstance();
        logger.info("Pre-processing input with unit: {}", unit.name);

        // Combine the user prompt with the input text.
        String fullUserPrompt = unit.userPrompt.replaceAll("\\{\\{input}}", inputText);

        // Log prompts to console
        console.log("  Provider: " + unit.provider + " | Model: " + unit.model);
        if (unit.systemPrompt != null && !unit.systemPrompt.trim().isEmpty()) {
            console.logPrompt("  System Prompt", unit.systemPrompt);
        }
        console.logPrompt("  User Prompt", fullUserPrompt);

        try {
            if (unit.provider.equalsIgnoreCase("OpenAI")) {
                console.log("  Calling OpenAI API...");
                String result = openAIClient.processText(unit.systemPrompt, fullUserPrompt, unit.model);
                console.logSuccess("API call completed");
                return result;
            } else if (unit.provider.equalsIgnoreCase("Open WebUI")) {
                console.log("  Calling Open WebUI...");
                String result = openWebUIClient.processText(unit.systemPrompt, fullUserPrompt, unit.model);
                console.logSuccess("API call completed");
                return result;
            }
        } catch (IOException e) {
            logger.error("Error processing with unit: {}", unit.name, e);
            console.logError("API call failed: " + e.getMessage());
        }
        return inputText;
    }
}