package com.urbantransport.reporting.event;

import com.urbantransport.reporting.service.DimensionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class IdentityEventsListenerTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRANCH_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private DimensionService dimensionService;

    @Captor
    private ArgumentCaptor<BranchEventPayload> payloadCaptor;

    private IdentityEventsListener listener;

    @BeforeEach
    void setUp() {
        listener = new IdentityEventsListener(jsonMapper, dimensionService);
    }

    @ParameterizedTest
    @ValueSource(strings = {EventTypes.BRANCH_CREATED, EventTypes.BRANCH_UPDATED})
    void branchEvent_isAppliedWithConvertedPayload(String eventType) {
        listener.onIdentityEvent(message(eventType, Map.of("branchId", BRANCH_ID.toString(), "name", "Central",
                "code", "CEN", "latitude", 14.6349, "longitude", -90.5069, "active", true)));

        verify(dimensionService).applyBranch(eq(EVENT_ID), eq(eventType), payloadCaptor.capture());
        BranchEventPayload payload = payloadCaptor.getValue();
        assertEquals(BRANCH_ID, payload.branchId());
        assertEquals("Central", payload.name());
        assertEquals("CEN", payload.code());
        assertEquals(0, new BigDecimal("14.6349").compareTo(payload.latitude()));
        assertEquals(0, new BigDecimal("-90.5069").compareTo(payload.longitude()));
        assertTrue(payload.active());
    }

    @Test
    void branchWithoutOptionalFields_isAppliedWithNulls() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("branchId", BRANCH_ID.toString());
        payload.put("name", "Central");
        payload.put("code", null);

        listener.onIdentityEvent(message(EventTypes.BRANCH_UPDATED, payload));

        verify(dimensionService).applyBranch(eq(EVENT_ID), eq(EventTypes.BRANCH_UPDATED), payloadCaptor.capture());
        assertNull(payloadCaptor.getValue().code());
        assertNull(payloadCaptor.getValue().latitude());
        assertNull(payloadCaptor.getValue().active());
    }

    @ParameterizedTest
    @ValueSource(strings = {"UserCreated", "UserDeactivated", "BranchDeleted"})
    void otherEvent_isIgnored(String eventType) {
        listener.onIdentityEvent(message(eventType, Map.of("userId", UUID.randomUUID().toString())));

        verifyNoInteractions(dimensionService);
    }

    @Test
    void eventWithoutType_isIgnored() {
        listener.onIdentityEvent(jsonMapper.writeValueAsString(Map.of("eventId", EVENT_ID.toString(),
                "payload", Map.of("branchId", BRANCH_ID.toString()))));

        verifyNoInteractions(dimensionService);
    }

    @Test
    void malformedMessage_throwsSoItGoesToRetryAndDlt() {
        assertThrows(JacksonException.class, () -> listener.onIdentityEvent("{no es json"));

        verifyNoInteractions(dimensionService);
    }

    private String message(String eventType, Map<String, Object> payload) {
        return jsonMapper.writeValueAsString(Map.of(
                "eventId", EVENT_ID.toString(),
                "eventType", eventType,
                "occurredAt", "2026-10-29T12:00:00Z",
                "aggregateId", BRANCH_ID.toString(),
                "version", 1,
                "payload", payload));
    }
}
