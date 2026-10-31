package com.urbantransport.reporting.web;

import com.urbantransport.reporting.AbstractIntegrationTest;
import com.urbantransport.reporting.event.EventTypes;
import com.urbantransport.reporting.exception.ErrorCodes;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReadModelApiIT extends AbstractIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(90);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaAdmin kafkaAdmin;

    @Autowired
    private JsonMapper jsonMapper;

    @Value("${app.kafka.topic.identity-events}")
    private String identityTopic;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Test
    void rebuild_clearsTablesAndReplaysEventsFromTheBeginning() throws Exception {
        UUID branchId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        send(branchId, envelope(eventId, branchId));
        await().atMost(TIMEOUT).untilAsserted(() -> assertTrue(dimBranchRepository.findById(branchId).isPresent()));
        UUID seededOnlyInDatabase = saveBranch("Sin evento", null, null);

        mockMvc.perform(post("/read-models/rebuild").with(systemAdmin()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.consumerGroup").value(groupId))
                .andExpect(jsonPath("$.clearedTables", hasItem("ledger_entries")))
                .andExpect(jsonPath("$.clearedTables", hasItem("processed_events")));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            assertTrue(dimBranchRepository.findById(branchId).isPresent());
            assertEquals(1, count("SELECT COUNT(*) FROM processed_events WHERE event_id = ?", eventId));
        });
        assertTrue(dimBranchRepository.findById(seededOnlyInDatabase).isEmpty());
        awaitConsumerCaughtUp();
    }

    @Test
    void rebuild_asBranchAdmin_isDeniedAndTablesStayIntact() throws Exception {
        UUID branchId = saveBranch("Xela", null, null);

        mockMvc.perform(post("/read-models/rebuild").with(branchAdmin(branchId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.ACCESS_DENIED));

        assertTrue(dimBranchRepository.findById(branchId).isPresent());
    }

    @Test
    void rebuild_withoutToken_returnsUnauthorized() throws Exception {
        UUID branchId = saveBranch("Xela", null, null);

        mockMvc.perform(post("/read-models/rebuild"))
                .andExpect(status().isUnauthorized());

        assertTrue(dimBranchRepository.findById(branchId).isPresent());
    }

    private void awaitConsumerCaughtUp() {
        try (Admin admin = Admin.create(kafkaAdmin.getConfigurationProperties())) {
            await().atMost(TIMEOUT).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
                Map<TopicPartition, OffsetAndMetadata> committed = admin.listConsumerGroupOffsets(groupId)
                        .partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);
                Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latest = admin.listOffsets(
                                committed.keySet().stream().collect(Collectors.toMap(partition -> partition,
                                        partition -> OffsetSpec.latest())))
                        .all().get(10, TimeUnit.SECONDS);
                committed.forEach((partition, offset) ->
                        assertEquals(latest.get(partition).offset(), offset.offset(), "lag en " + partition));
            });
        }
    }

    private void send(UUID key, String value) throws Exception {
        kafkaTemplate.send(identityTopic, key.toString(), value).get(10, TimeUnit.SECONDS);
    }

    private String envelope(UUID eventId, UUID branchId) {
        return jsonMapper.writeValueAsString(Map.of(
                "eventId", eventId.toString(),
                "eventType", EventTypes.BRANCH_CREATED,
                "occurredAt", Instant.now().toString(),
                "aggregateId", branchId.toString(),
                "version", 1,
                "payload", Map.of("branchId", branchId.toString(), "name", "Xela", "code", "XEL", "active", true)));
    }
}
