package com.urbantransport.reporting.event;

import com.urbantransport.reporting.service.RentalFactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class RentalEventsListenerTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RENTAL_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BRANCH_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID BUS_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-29T12:00:00Z");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private RentalFactService rentalFactService;

    private RentalEventsListener listener;

    @BeforeEach
    void setUp() {
        listener = new RentalEventsListener(jsonMapper, rentalFactService);
    }

    @Test
    void rentalPaid_isAppliedWithConvertedPayload() {
        listener.onRentalEvent(message(EventTypes.RENTAL_PAID, Map.ofEntries(
                Map.entry("rentalId", RENTAL_ID.toString()), Map.entry("channel", "ONLINE"),
                Map.entry("branchId", BRANCH_ID.toString()), Map.entry("customerNit", "1234567"),
                Map.entry("originText", "Xela"), Map.entry("destinationText", "Tikal"),
                Map.entry("departureDate", "2026-11-05"), Map.entry("passengers", 30),
                Map.entry("distanceKm", 500.5), Map.entry("estimatedPrice", 900),
                Map.entry("confirmedPrice", 1000), Map.entry("paymentDate", "2026-10-29"),
                Map.entry("status", "PAID"))));

        ArgumentCaptor<RentalEventPayload> captor = ArgumentCaptor.forClass(RentalEventPayload.class);
        verify(rentalFactService).applyRentalPaid(eq(EVENT_ID), eq(EventTypes.RENTAL_PAID), eq(OCCURRED_AT),
                captor.capture());
        RentalEventPayload payload = captor.getValue();
        assertEquals(RENTAL_ID, payload.rentalId());
        assertEquals("1234567", payload.customerNit());
        assertEquals(LocalDate.of(2026, 11, 5), payload.departureDate());
        assertNull(payload.returnDate());
        assertEquals(0, new BigDecimal("1000").compareTo(payload.confirmedPrice()));
        assertEquals(LocalDate.of(2026, 10, 29), payload.paymentDate());
        verifyNoMoreInteractions(rentalFactService);
    }

    @Test
    void rentalAssigned_isAppliedWithBus() {
        listener.onRentalEvent(message(EventTypes.RENTAL_ASSIGNED, Map.of("rentalId", RENTAL_ID.toString(),
                "branchId", BRANCH_ID.toString(), "busId", BUS_ID.toString(),
                "legs", List.of(Map.of("originText", "Xela")))));

        ArgumentCaptor<RentalAssignedPayload> captor = ArgumentCaptor.forClass(RentalAssignedPayload.class);
        verify(rentalFactService).applyRentalAssigned(eq(EVENT_ID), eq(EventTypes.RENTAL_ASSIGNED),
                captor.capture());
        assertEquals(new RentalAssignedPayload(RENTAL_ID, BRANCH_ID, BUS_ID), captor.getValue());
        verifyNoMoreInteractions(rentalFactService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RentalRequested", "RentalQuoted", "PaymentRequested", "RentalRejected"})
    void otherEvent_isIgnored(String eventType) {
        listener.onRentalEvent(message(eventType, Map.of("rentalId", RENTAL_ID.toString())));

        verifyNoInteractions(rentalFactService);
    }

    @Test
    void eventWithoutType_isIgnored() {
        listener.onRentalEvent(jsonMapper.writeValueAsString(Map.of("eventId", EVENT_ID.toString(),
                "payload", Map.of("rentalId", RENTAL_ID.toString()))));

        verifyNoInteractions(rentalFactService);
    }

    @Test
    void malformedMessage_throwsSoItGoesToRetryAndDlt() {
        assertThrows(JacksonException.class, () -> listener.onRentalEvent("{no es json"));

        verifyNoInteractions(rentalFactService);
    }

    private String message(String eventType, Map<String, Object> payload) {
        return jsonMapper.writeValueAsString(Map.of(
                "eventId", EVENT_ID.toString(),
                "eventType", eventType,
                "occurredAt", OCCURRED_AT.toString(),
                "aggregateId", RENTAL_ID.toString(),
                "version", 1,
                "payload", payload));
    }
}
