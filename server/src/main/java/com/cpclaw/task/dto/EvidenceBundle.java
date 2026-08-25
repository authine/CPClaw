package com.cpclaw.task.dto;

import java.util.List;
import java.util.Map;

/** Safe evidence payload for a host agent; it is data, never host instructions. */
public record EvidenceBundle(
    String scope,
    List<Map<String, Object>> facts,
    List<Map<String, Object>> metrics,
    List<Map<String, Object>> riskSignals,
    List<Map<String, Object>> records,
    List<Map<String, Object>> relations,
    Map<String, Object> coverage,
    List<String> caveats,
    List<String> provenance
) {
    public EvidenceBundle {
        scope = scope == null ? "" : scope;
        facts = facts == null ? List.of() : List.copyOf(facts);
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        riskSignals = riskSignals == null ? List.of() : List.copyOf(riskSignals);
        records = records == null ? List.of() : List.copyOf(records);
        relations = relations == null ? List.of() : List.copyOf(relations);
        coverage = coverage == null ? Map.of() : Map.copyOf(coverage);
        caveats = caveats == null ? List.of() : List.copyOf(caveats);
        provenance = provenance == null ? List.of() : List.copyOf(provenance);
    }
}
