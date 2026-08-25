package com.cpclaw.conversation;

import com.cpclaw.conversation.entity.QueryResultReference;
import com.cpclaw.conversation.repository.QueryResultReferenceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class QueryResultReferenceService {

    private static final long REFERENCE_TTL_MINUTES = 30;

    private final QueryResultReferenceRepository repository;
    private final ObjectMapper objectMapper;

    public QueryResultReferenceService(QueryResultReferenceRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public List<QueryResultReference> storeDisplayedRecords(
        String conversationId,
        String messageId,
        String agentRunId,
        String appCode,
        String schemaCode,
        List<Map<String, Object>> records
    ) {
        if (isBlank(conversationId) || isBlank(messageId) || isBlank(agentRunId) || isBlank(appCode) || isBlank(schemaCode) || records == null || records.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plus(REFERENCE_TTL_MINUTES, ChronoUnit.MINUTES);
        List<QueryResultReference> references = new ArrayList<>();
        for (int index = 0; index < records.size(); index++) {
            Map<String, Object> record = records.get(index);
            String recordId = recordId(record);
            if (isBlank(recordId)) {
                continue;
            }
            QueryResultReference reference = new QueryResultReference();
            reference.setId(UUID.randomUUID().toString());
            reference.setConversationId(conversationId);
            reference.setMessageId(messageId);
            reference.setAgentRunId(agentRunId);
            reference.setAppCode(appCode);
            reference.setSchemaCode(schemaCode);
            reference.setRecordId(recordId);
            reference.setRowIndex(index + 1);
            reference.setDisplaySnapshotJson(displaySnapshot(recordId, record));
            reference.setCreatedAt(now);
            reference.setExpiresAt(expiresAt);
            references.add(reference);
        }
        return references.isEmpty() ? List.of() : repository.saveAll(references);
    }

    public long deleteByConversationId(String conversationId) {
        return repository.deleteByConversationId(conversationId);
    }

    public boolean canResolveOrdinal(String conversationId, String schemaCode, String userMessage) {
        try {
            resolveOrdinal(conversationId, schemaCode, userMessage);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public ResultReferenceTarget resolveOrdinal(String conversationId, String schemaCode, String userMessage) {
        if (isBlank(conversationId) || isBlank(schemaCode)) {
            throw new IllegalArgumentException("当前操作缺少会话或业务对象，无法解析已展示的记录。");
        }
        OptionalInt ordinal = parseOrdinal(userMessage);
        if (ordinal.isEmpty()) {
            throw new IllegalArgumentException("删除操作必须引用当前会话中已展示的第几条记录，例如“删除第一条”。");
        }
        QueryResultReference latest = repository.findFirstByConversationIdAndSchemaCodeAndExpiresAtAfterOrderByCreatedAtDesc(
            conversationId,
            schemaCode,
            Instant.now()
        );
        if (latest == null) {
            throw new IllegalArgumentException("当前会话中没有仍有效的“" + schemaCode + "”明细列表。请先查询列表后再指定第几条记录。");
        }
        QueryResultReference target = repository.findByConversationIdAndMessageIdOrderByRowIndexAsc(conversationId, latest.getMessageId()).stream()
            .filter(reference -> schemaCode.equals(reference.getSchemaCode()))
            .filter(reference -> reference.getRowIndex() == ordinal.getAsInt())
            .filter(reference -> reference.getExpiresAt().isAfter(Instant.now()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("上一轮已展示的列表中不存在第 " + ordinal.getAsInt() + " 条有效记录。请重新查询后再操作。"));
        return new ResultReferenceTarget(
            target.getId(),
            target.getMessageId(),
            target.getAgentRunId(),
            target.getAppCode(),
            target.getSchemaCode(),
            target.getRecordId(),
            target.getRowIndex(),
            target.getExpiresAt()
        );
    }

    public void validateTarget(String conversationId, String referenceId, String appCode, String schemaCode, String recordId) {
        QueryResultReference reference = repository.findById(referenceId)
            .orElseThrow(() -> new IllegalArgumentException("确认单引用的查询记录不存在，无法执行。"));
        if (!reference.getConversationId().equals(conversationId)
            || !reference.getAppCode().equals(appCode)
            || !reference.getSchemaCode().equals(schemaCode)
            || !reference.getRecordId().equals(recordId)) {
            throw new IllegalArgumentException("确认单引用与当前删除目标不一致，已阻止执行。");
        }
        if (!reference.getExpiresAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("确认单引用的列表记录已过期，请重新查询后再操作。");
        }
    }

    private String recordId(Map<String, Object> record) {
        if (record == null) {
            return "";
        }
        for (String key : List.of("id", "objectId", "dataId", "bizObjectId")) {
            Object value = record.get(key);
            if (value != null && !isBlank(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        Object data = record.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            for (String key : List.of("id", "objectId", "dataId", "bizObjectId")) {
                Object value = dataMap.get(key);
                if (value != null && !isBlank(String.valueOf(value))) {
                    return String.valueOf(value).trim();
                }
            }
        }
        return "";
    }

    private String displaySnapshot(String recordId, Map<String, Object> record) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("recordId", recordId);
        Object data = record == null ? null : record.get("data");
        Map<?, ?> values = data instanceof Map<?, ?> dataMap ? dataMap : record;
        for (String key : List.of("instanceName", "name", "title", "displayName", "label")) {
            Object value = values == null ? null : values.get(key);
            if (value != null && !isBlank(String.valueOf(value))) {
                snapshot.put("displayName", String.valueOf(value));
                break;
            }
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            return "{\"recordId\":\"" + recordId.replace("\"", "") + "\"}";
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private OptionalInt parseOrdinal(String content) {
        String value = content == null ? "" : content.replaceAll("\\s+", "");
        Matcher numeric = Pattern.compile("第\\s*([1-9][0-9]*)\\s*(?:条|个|笔|单)").matcher(value);
        if (numeric.find()) {
            return OptionalInt.of(Integer.parseInt(numeric.group(1)));
        }
        Matcher chinese = Pattern.compile("第\\s*([一二三四五六七八九十两])\\s*(?:条|个|笔|单)").matcher(value);
        if (!chinese.find()) {
            return OptionalInt.empty();
        }
        return switch (chinese.group(1)) {
            case "一" -> OptionalInt.of(1);
            case "二", "两" -> OptionalInt.of(2);
            case "三" -> OptionalInt.of(3);
            case "四" -> OptionalInt.of(4);
            case "五" -> OptionalInt.of(5);
            case "六" -> OptionalInt.of(6);
            case "七" -> OptionalInt.of(7);
            case "八" -> OptionalInt.of(8);
            case "九" -> OptionalInt.of(9);
            case "十" -> OptionalInt.of(10);
            default -> OptionalInt.empty();
        };
    }

    public record ResultReferenceTarget(
        String referenceId,
        String messageId,
        String agentRunId,
        String appCode,
        String schemaCode,
        String recordId,
        int rowIndex,
        Instant expiresAt
    ) { }
}
