package com.cpclaw.cloudpivot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "cpclaw.cloudpivot.runtime")
public class CloudPivotRuntimeProperties {

    @Valid
    private Query query = new Query();
    @Valid
    private Connector connector = new Connector();
    @Valid
    private Display display = new Display();

    public Query getQuery() {
        return query;
    }

    public void setQuery(Query query) {
        this.query = query;
    }

    public Connector getConnector() {
        return connector;
    }

    public void setConnector(Connector connector) {
        this.connector = connector;
    }

    public Display getDisplay() {
        return display;
    }

    public void setDisplay(Display display) {
        this.display = display;
    }

    public static class Query {
        @Min(1) private int countPageSize;
        @Min(1) private int listPageSize;
        @Min(1) private int analysisPageSize;
        @Min(1) private int yearlyPageSize;
        @Min(1) private int countRecordLimit;
        @Min(1) private int listRecordLimit;
        @Min(1) private int dimensionRecordLimit;
        @Min(1) private int analysisRecordLimit;
        @Min(1) private int yearlyRecordLimit;

        public int getCountPageSize() { return countPageSize; }
        public void setCountPageSize(int countPageSize) { this.countPageSize = countPageSize; }
        public int getListPageSize() { return listPageSize; }
        public void setListPageSize(int listPageSize) { this.listPageSize = listPageSize; }
        public int getAnalysisPageSize() { return analysisPageSize; }
        public void setAnalysisPageSize(int analysisPageSize) { this.analysisPageSize = analysisPageSize; }
        public int getYearlyPageSize() { return yearlyPageSize; }
        public void setYearlyPageSize(int yearlyPageSize) { this.yearlyPageSize = yearlyPageSize; }
        public int getCountRecordLimit() { return countRecordLimit; }
        public void setCountRecordLimit(int countRecordLimit) { this.countRecordLimit = countRecordLimit; }
        public int getListRecordLimit() { return listRecordLimit; }
        public void setListRecordLimit(int listRecordLimit) { this.listRecordLimit = listRecordLimit; }
        public int getDimensionRecordLimit() { return dimensionRecordLimit; }
        public void setDimensionRecordLimit(int dimensionRecordLimit) { this.dimensionRecordLimit = dimensionRecordLimit; }
        public int getAnalysisRecordLimit() { return analysisRecordLimit; }
        public void setAnalysisRecordLimit(int analysisRecordLimit) { this.analysisRecordLimit = analysisRecordLimit; }
        public int getYearlyRecordLimit() { return yearlyRecordLimit; }
        public void setYearlyRecordLimit(int yearlyRecordLimit) { this.yearlyRecordLimit = yearlyRecordLimit; }
    }

    public static class Connector {
        @Min(1) private int requestTimeoutSeconds;
        @Min(1) private int maxAnalysisRecords;
        @Min(1) private int maxPageSize;
        @Min(1) private int maxDetailEnrichRecords;
        @Min(1) private int maxListDetailRecords;
        @Min(1) private int detailEnrichParallelism;
        @Min(1) private int dataItemProbeParallelism;

        public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
        public void setRequestTimeoutSeconds(int requestTimeoutSeconds) { this.requestTimeoutSeconds = requestTimeoutSeconds; }
        public int getMaxAnalysisRecords() { return maxAnalysisRecords; }
        public void setMaxAnalysisRecords(int maxAnalysisRecords) { this.maxAnalysisRecords = maxAnalysisRecords; }
        public int getMaxPageSize() { return maxPageSize; }
        public void setMaxPageSize(int maxPageSize) { this.maxPageSize = maxPageSize; }
        public int getMaxDetailEnrichRecords() { return maxDetailEnrichRecords; }
        public void setMaxDetailEnrichRecords(int maxDetailEnrichRecords) { this.maxDetailEnrichRecords = maxDetailEnrichRecords; }
        public int getMaxListDetailRecords() { return maxListDetailRecords; }
        public void setMaxListDetailRecords(int maxListDetailRecords) { this.maxListDetailRecords = maxListDetailRecords; }
        public int getDetailEnrichParallelism() { return detailEnrichParallelism; }
        public void setDetailEnrichParallelism(int detailEnrichParallelism) { this.detailEnrichParallelism = detailEnrichParallelism; }
        public int getDataItemProbeParallelism() { return dataItemProbeParallelism; }
        public void setDataItemProbeParallelism(int dataItemProbeParallelism) { this.dataItemProbeParallelism = dataItemProbeParallelism; }
    }

    public static class Display {
        @Min(1) private int maxFields;
        @Min(1) private int maxValueLength;
        @Min(0) private int defaultPriority;
        @NotEmpty private List<String> nestedValueKeys = new ArrayList<>();
        private List<String> hiddenExact = new ArrayList<>();
        private List<String> hiddenContains = new ArrayList<>();
        private List<String> hiddenSuffixes = new ArrayList<>();
        private List<String> identifierPatterns = new ArrayList<>();
        @Valid private List<FieldRule> fieldRules = new ArrayList<>();
        private Map<String, String> fallbackLabels = new LinkedHashMap<>();

        public int getMaxFields() { return maxFields; }
        public void setMaxFields(int maxFields) { this.maxFields = maxFields; }
        public int getMaxValueLength() { return maxValueLength; }
        public void setMaxValueLength(int maxValueLength) { this.maxValueLength = maxValueLength; }
        public int getDefaultPriority() { return defaultPriority; }
        public void setDefaultPriority(int defaultPriority) { this.defaultPriority = defaultPriority; }
        public List<String> getNestedValueKeys() { return nestedValueKeys; }
        public void setNestedValueKeys(List<String> nestedValueKeys) { this.nestedValueKeys = nestedValueKeys; }
        public List<String> getHiddenExact() { return hiddenExact; }
        public void setHiddenExact(List<String> hiddenExact) { this.hiddenExact = hiddenExact; }
        public List<String> getHiddenContains() { return hiddenContains; }
        public void setHiddenContains(List<String> hiddenContains) { this.hiddenContains = hiddenContains; }
        public List<String> getHiddenSuffixes() { return hiddenSuffixes; }
        public void setHiddenSuffixes(List<String> hiddenSuffixes) { this.hiddenSuffixes = hiddenSuffixes; }
        public List<String> getIdentifierPatterns() { return identifierPatterns; }
        public void setIdentifierPatterns(List<String> identifierPatterns) { this.identifierPatterns = identifierPatterns; }
        public List<FieldRule> getFieldRules() { return fieldRules; }
        public void setFieldRules(List<FieldRule> fieldRules) { this.fieldRules = fieldRules; }
        public Map<String, String> getFallbackLabels() { return fallbackLabels; }
        public void setFallbackLabels(Map<String, String> fallbackLabels) { this.fallbackLabels = fallbackLabels; }
    }

    public static class FieldRule {
        private String label;
        @Min(0) private int priority;
        private List<String> exact = new ArrayList<>();
        private List<String> contains = new ArrayList<>();

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
        public List<String> getExact() { return exact; }
        public void setExact(List<String> exact) { this.exact = exact; }
        public List<String> getContains() { return contains; }
        public void setContains(List<String> contains) { this.contains = contains; }
    }
}
