package org.whispercat.postprocessing;

import java.util.ArrayList;
import java.util.List;

/**
 * Data model class representing an executable pipeline.
 * A Pipeline is an ordered sequence of ProcessingUnit references.
 */
public class Pipeline {
    public String uuid;             // Unique identifier
    public String title;            // Display name for this pipeline
    public String description;      // Optional description
    public boolean enabled = true;  // Whether this entire pipeline is active
    public List<PipelineUnitReference> unitReferences = new ArrayList<>();

    public Pipeline() {
    }

    public Pipeline(String uuid, String title, String description) {
        this.uuid = uuid;
        this.title = title;
        this.description = description;
    }
}
