package com.cpclaw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cpclaw.conversation.entity.Message;
import com.cpclaw.conversation.repository.MessageRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.io.ClassPathResource;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:cpclaw-message-storage;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MessageStorageTests {

    @Autowired
    private MessageRepository messageRepository;

    @Test
    void assistantMessagesCanPersistPayloadsLargerThanMysqlTextLimit() {
        String longContent = "stage distribution\n" + "stage=qualification,count=1;".repeat(4_000);
        String longMetadata = "{\"trace\":\"" + "observe-think-act-reflect;".repeat(4_000) + "\"}";
        assertTrue(longContent.length() > 65_535);
        assertTrue(longMetadata.length() > 65_535);

        Message message = new Message();
        message.setId(UUID.randomUUID().toString());
        message.setConversationId(UUID.randomUUID().toString());
        message.setRole("assistant");
        message.setContent(longContent);
        message.setMetadataJson(longMetadata);
        message.setThinkingEnabled(false);
        message.setCreatedAt(Instant.now());

        messageRepository.saveAndFlush(message);

        Message stored = messageRepository.findById(message.getId()).orElseThrow();
        assertEquals(longContent.length(), stored.getContent().length());
        assertEquals(longMetadata.length(), stored.getMetadataJson().length());
    }

    @Test
    void migrationExpandsMessageAndAgentTraceColumnsToLongText() throws Exception {
        String migration = new ClassPathResource("db/migration/V2__expand_message_and_audit_text_columns.sql")
            .getContentAsString(StandardCharsets.UTF_8);

        assertTrue(migration.contains("ALTER TABLE messages MODIFY COLUMN content LONGTEXT NOT NULL;"));
        assertTrue(migration.contains("ALTER TABLE messages MODIFY COLUMN metadata_json LONGTEXT;"));
        assertTrue(migration.contains("ALTER TABLE agent_runs MODIFY COLUMN plan_json LONGTEXT;"));
        assertTrue(migration.contains("ALTER TABLE tool_calls MODIFY COLUMN output_json_masked LONGTEXT;"));
    }

    @Test
    void migrationCreatesCloudPivotDataItemsAndRelationSourceDataItemColumn() throws Exception {
        String migration = new ClassPathResource("db/migration/V3__cloudpivot_data_items_and_relations.sql")
            .getContentAsString(StandardCharsets.UTF_8);

        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS cloudpivot_data_items"));
        assertTrue(migration.contains("data_item_code VARCHAR(128) NOT NULL"));
        assertTrue(migration.contains("FROM cloudpivot_entity_fields legacy"));
        assertTrue(migration.contains("source_data_item_id VARCHAR(36) NULL"));
        assertTrue(migration.contains("SET source_data_item_id = source_field_id"));
    }

    @Test
    void migrationExpandsCloudPivotMetadataRawJsonColumnsToLongText() throws Exception {
        String migration = new ClassPathResource("db/migration/V4__expand_cloudpivot_metadata_raw_json.sql")
            .getContentAsString(StandardCharsets.UTF_8);

        assertTrue(migration.contains("ALTER TABLE cloudpivot_apps MODIFY COLUMN raw_json LONGTEXT;"));
        assertTrue(migration.contains("ALTER TABLE cloudpivot_entities MODIFY COLUMN raw_json LONGTEXT;"));
        assertTrue(migration.contains("ALTER TABLE cloudpivot_data_items MODIFY COLUMN raw_json LONGTEXT;"));
        assertTrue(migration.contains("ALTER TABLE cloudpivot_entity_relations MODIFY COLUMN raw_json LONGTEXT;"));
    }
}
