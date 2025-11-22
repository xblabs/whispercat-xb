package org.whispercat.postprocessing;

import org.whispercat.ConfigManager;
import org.whispercat.ConsoleLogger;
import org.whispercat.Notificationmanager;
import org.whispercat.ToastNotification;
import org.whispercat.postprocessing.clients.OpenWebUIProcessClient;
import org.whispercat.recording.OpenAIClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
     * Automatically optimizes consecutive units with the same provider/model into single API calls.
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

        // Group units into batches for optimization
        List<UnitBatch> batches = groupUnitsIntoBatches(pipeline);

        // Count total enabled units
        int enabledUnitCount = 0;
        for (UnitBatch batch : batches) {
            enabledUnitCount += batch.units.size();
        }

        // Log pipeline start
        console.separator();
        console.log("Starting pipeline: " + pipeline.title);
        console.log("Enabled units: " + enabledUnitCount);

        // Log optimization summary
        int optimizedCount = 0;
        for (UnitBatch batch : batches) {
            if (batch.isOptimizable) {
                optimizedCount += batch.units.size();
            }
        }
        if (optimizedCount > 0) {
            int savedCalls = optimizedCount - (int) batches.stream().filter(b -> b.isOptimizable).count();
            console.log("⚡ Optimization: " + savedCalls + " API call(s) saved by chaining");
        }
        console.separator();

        // Execute each batch
        String processedText = originalText;
        for (int i = 0; i < batches.size(); i++) {
            UnitBatch batch = batches.get(i);
            processedText = executeBatch(processedText, batch, i + 1, batches.size());
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

    /**
     * Inner class representing a batch of units that can be optimized into a single API call.
     */
    private static class UnitBatch {
        List<ProcessingUnit> units = new ArrayList<>();
        boolean isOptimizable = false; // true if 2+ prompt units with same provider/model
        String provider;
        String model;

        boolean canAddUnit(ProcessingUnit unit) {
            if (!"Prompt".equalsIgnoreCase(unit.type)) {
                return false; // Text replacement breaks the chain
            }
            if (units.isEmpty()) {
                return true; // First unit in batch
            }
            // Check if same provider and model
            return provider.equals(unit.provider) && model.equals(unit.model);
        }

        void addUnit(ProcessingUnit unit) {
            units.add(unit);
            if (units.size() == 1) {
                provider = unit.provider;
                model = unit.model;
            }
            if (units.size() >= 2) {
                isOptimizable = true;
            }
        }
    }

    /**
     * Groups consecutive units with the same provider/model into batches for optimization.
     * Text Replacement units break the chain.
     *
     * @param pipeline The pipeline to analyze
     * @return List of batches, each batch contains 1+ units
     */
    private List<UnitBatch> groupUnitsIntoBatches(Pipeline pipeline) {
        List<UnitBatch> batches = new ArrayList<>();
        UnitBatch currentBatch = null;

        for (PipelineUnitReference ref : pipeline.unitReferences) {
            if (!ref.enabled) {
                continue; // Skip disabled units
            }

            ProcessingUnit unit = configManager.getProcessingUnitByUuid(ref.unitUuid);
            if (unit == null) {
                continue; // Skip missing units
            }

            // Check if we can add to current batch
            if (currentBatch != null && currentBatch.canAddUnit(unit)) {
                currentBatch.addUnit(unit);
            } else {
                // Start new batch
                if (currentBatch != null) {
                    batches.add(currentBatch);
                }
                currentBatch = new UnitBatch();
                currentBatch.addUnit(unit);
            }
        }

        // Add final batch
        if (currentBatch != null && !currentBatch.units.isEmpty()) {
            batches.add(currentBatch);
        }

        return batches;
    }

    /**
     * Compiles a chained prompt for multiple units using Style 1 (explicit variable naming).
     * This creates a single prompt that executes all units in sequence with clear data flow.
     *
     * @param inputText The initial input text
     * @param batch The batch of units to chain
     * @return Array: [systemPrompt, userPrompt]
     */
    private String[] compileChainedPrompt(String inputText, UnitBatch batch) {
        StringBuilder systemPrompt = new StringBuilder();
        StringBuilder userPrompt = new StringBuilder();

        // System prompt: explain the chaining and list step contexts
        systemPrompt.append("Execute transformations sequentially. Each step's output becomes the next step's input.\n\n");
        systemPrompt.append("Step Contexts:\n");

        for (int i = 0; i < batch.units.size(); i++) {
            ProcessingUnit unit = batch.units.get(i);
            systemPrompt.append((i + 1)).append(". ");
            if (unit.systemPrompt != null && !unit.systemPrompt.trim().isEmpty()) {
                systemPrompt.append(unit.systemPrompt);
            } else {
                systemPrompt.append("No specific context");
            }
            systemPrompt.append("\n");
        }

        // User prompt: define INPUT_TEXT and each STEP
        userPrompt.append("INPUT_TEXT:\n\"\"\"\n").append(inputText).append("\n\"\"\"\n\n");

        for (int i = 0; i < batch.units.size(); i++) {
            ProcessingUnit unit = batch.units.get(i);
            String instruction = unit.userPrompt;

            // Replace {{input}} with the appropriate variable
            if (i == 0) {
                // First step uses INPUT_TEXT
                instruction = instruction.replaceAll("\\{\\{input}}", "INPUT_TEXT");
            } else {
                // Subsequent steps use previous step's output
                instruction = instruction.replaceAll("\\{\\{input}}", "STEP" + i + "_OUTPUT");
            }

            userPrompt.append("STEP_").append(i + 1).append(": ").append(instruction)
                    .append("\n→ Store result as: STEP").append(i + 1).append("_OUTPUT\n\n");
        }

        // Final instruction
        userPrompt.append("Return only: STEP").append(batch.units.size()).append("_OUTPUT");

        return new String[]{systemPrompt.toString(), userPrompt.toString()};
    }

    /**
     * Executes a batch of units. If optimizable (2+ units with same model), compiles a chained prompt.
     * Otherwise executes units individually.
     *
     * @param inputText The input text for this batch
     * @param batch The batch to execute
     * @param batchNumber Current batch number for logging
     * @param totalBatches Total number of batches
     * @return The output text after processing this batch
     */
    private String executeBatch(String inputText, UnitBatch batch, int batchNumber, int totalBatches) {
        ConsoleLogger console = ConsoleLogger.getInstance();

        if (batch.isOptimizable) {
            // Optimized: execute as single API call with chained prompt
            int savedCalls = batch.units.size() - 1;
            console.separator();
            console.log("⚡ PIPELINE OPTIMIZATION ACTIVE");
            console.log("  Merging " + batch.units.size() + " consecutive " + batch.provider + "/" + batch.model + " units");
            console.log("  Benefit: " + savedCalls + " API call" + (savedCalls > 1 ? "s" : "") + " saved, " +
                       (savedCalls * 100 / batch.units.size()) + "% cost reduction");
            console.separator();

            // Log each unit that's being merged
            console.log("  Units being merged:");
            for (int i = 0; i < batch.units.size(); i++) {
                ProcessingUnit unit = batch.units.get(i);
                console.log("    " + (i + 1) + ". " + unit.name);
            }

            // Compile the chained prompt
            String[] prompts = compileChainedPrompt(inputText, batch);
            String systemPrompt = prompts[0];
            String userPrompt = prompts[1];

            // Log the compiled prompts
            console.log("");
            console.logPrompt("  Compiled System Prompt", systemPrompt);
            console.logPrompt("  Compiled User Prompt", userPrompt);
            console.log("");
            console.log("  Executing optimized chain...");

            try {
                String result;
                if (batch.provider.equalsIgnoreCase("OpenAI")) {
                    result = openAIClient.processText(systemPrompt, userPrompt, batch.model);
                } else if (batch.provider.equalsIgnoreCase("Open WebUI")) {
                    result = openWebUIClient.processText(systemPrompt, userPrompt, batch.model);
                } else {
                    console.logError("Unknown provider: " + batch.provider);
                    return inputText;
                }
                console.separator();
                console.logSuccess("✓ Optimized chain completed - " + savedCalls + " API call" +
                                 (savedCalls > 1 ? "s" : "") + " saved!");
                console.separator();
                return result;
            } catch (IOException e) {
                logger.error("Error executing chained prompt", e);
                console.logError("Chained API call failed: " + e.getMessage());
                console.separator();
                return inputText;
            }

        } else {
            // Not optimizable: execute units individually
            String processedText = inputText;
            for (int i = 0; i < batch.units.size(); i++) {
                ProcessingUnit unit = batch.units.get(i);

                // Log unit start
                console.logStep(unit.name + " (" + unit.type + ")", batchNumber, totalBatches);

                if ("Prompt".equalsIgnoreCase(unit.type)) {
                    processedText = performPromptProcessingWithUnit(processedText, unit, batchNumber, totalBatches);
                } else if ("Text Replacement".equalsIgnoreCase(unit.type)) {
                    console.log("  Replacing: '" + unit.textToReplace + "' → '" + unit.replacementText + "'");
                    processedText = processedText.replace(unit.textToReplace, unit.replacementText);
                    console.logSuccess("Text replacement completed");
                }
            }
            return processedText;
        }
    }
}