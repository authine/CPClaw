package com.cpclaw.insight;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cpclaw.skill.yunshu.YunshuVisualizationPlanner;

/** Compatibility facade; visualization semantics belong to the Yunshu Skill. */
public class InsightVisualizationPlanner extends YunshuVisualizationPlanner {
    public InsightVisualizationPlanner(ObjectMapper objectMapper) {
        super(objectMapper);
    }
}
