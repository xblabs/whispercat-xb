package org.whispercat.postprocessing;

/**
 * Data model class representing a reusable processing unit in the library.
 * A ProcessingUnit is a template that can be referenced by multiple pipelines.
 */
public class ProcessingUnit {
    public String uuid;             // Unique identifier
    public String name;             // Display name for this unit
    public String description;      // Optional description
    public String type;             // "Prompt" or "Text Replacement"

    // For Prompt:
    public String provider;         // "OpenAI" or "Open WebUI"
    public String model;
    public String systemPrompt;
    public String userPrompt;

    // For Text Replacement:
    public String textToReplace;
    public String replacementText;

    // TODO: Text to Speech in future
}
