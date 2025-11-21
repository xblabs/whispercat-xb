package org.whispercat.postprocessing;

/**
 * Data model class representing a reference to a ProcessingUnit within a Pipeline.
 * This allows the same unit to be reused in multiple pipelines with different enabled states.
 */
public class PipelineUnitReference {
    public String unitUuid;         // References a ProcessingUnit by UUID
    public boolean enabled = true;  // Whether this unit is enabled in this pipeline

    public PipelineUnitReference() {
    }

    public PipelineUnitReference(String unitUuid, boolean enabled) {
        this.unitUuid = unitUuid;
        this.enabled = enabled;
    }
}
