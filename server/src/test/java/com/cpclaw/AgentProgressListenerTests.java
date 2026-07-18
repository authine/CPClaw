package com.cpclaw;

import static org.assertj.core.api.Assertions.assertThat;

import com.cpclaw.agent.AgentProgressListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentProgressListenerTests {

    @Test
    void executionOnlyListenerDefersAnswerEventsButKeepsExecutionEvents() {
        List<String> events = new ArrayList<>();
        AgentProgressListener delegate = new AgentProgressListener() {
            @Override
            public void onProgress(String title, String status) {
                events.add("progress:" + title);
            }

            @Override
            public void onThought(String phase, String title, String status, String state) {
                events.add("thought:" + phase);
            }

            @Override
            public void onExecution(String title, String status, Map<String, Object> data, String state) {
                events.add("execution:" + title);
            }

            @Override
            public void onAnswerStart(String mode) {
                events.add("answer-start");
            }

            @Override
            public void onAnswerChunk(String content) {
                events.add("answer-chunk");
            }

            @Override
            public void onAnswerComplete(String mode) {
                events.add("answer-complete");
            }
        };

        AgentProgressListener listener = AgentProgressListener.withoutAnswerEvents(delegate);
        listener.onThought("verify", "校验", "完成", "completed");
        listener.onExecution("查询", "完成", Map.of("total", 1), "completed");
        listener.onAnswerStart("model");
        listener.onAnswerChunk("正文");
        listener.onAnswerComplete("model");

        assertThat(events).containsExactly("thought:verify", "execution:查询");
    }
}
