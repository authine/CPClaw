package com.cpclaw.metadata.graph;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "cpclaw.metadata.graphify")
public class MetadataGraphProperties {
    private boolean enabled = true;
    private boolean rebuildOnMetadataSync = true;
    private boolean includeApiEndpoints = true;
    private boolean writeExport = true;
    @NotBlank private String outputDirectory = "./storage/graphify-out";
    @Min(100) private int batchSize = 500;
    @Min(1) @Max(5) private int maxDepth = 3;
    @Min(10) @Max(2000) private int maxNodes = 500;
    @Min(1) @Max(10) private int snapshotRetention = 2;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isRebuildOnMetadataSync() { return rebuildOnMetadataSync; }
    public void setRebuildOnMetadataSync(boolean rebuildOnMetadataSync) { this.rebuildOnMetadataSync = rebuildOnMetadataSync; }
    public boolean isIncludeApiEndpoints() { return includeApiEndpoints; }
    public void setIncludeApiEndpoints(boolean includeApiEndpoints) { this.includeApiEndpoints = includeApiEndpoints; }
    public boolean isWriteExport() { return writeExport; }
    public void setWriteExport(boolean writeExport) { this.writeExport = writeExport; }
    public String getOutputDirectory() { return outputDirectory; }
    public void setOutputDirectory(String outputDirectory) { this.outputDirectory = outputDirectory; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getMaxDepth() { return maxDepth; }
    public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }
    public int getMaxNodes() { return maxNodes; }
    public void setMaxNodes(int maxNodes) { this.maxNodes = maxNodes; }
    public int getSnapshotRetention() { return snapshotRetention; }
    public void setSnapshotRetention(int snapshotRetention) { this.snapshotRetention = snapshotRetention; }
}
