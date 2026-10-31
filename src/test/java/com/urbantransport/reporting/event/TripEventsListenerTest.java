package com.urbantransport.reporting.event;

import com.urbantransport.reporting.service.TripFactService;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TripEventsListenerTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TRIP_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BUS_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID RENTAL_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private TripFactService tripFactService;

    @Captor
    private ArgumentCaptor<TripEventPayload> payloadCaptor;

    private TripEventsListener listener;

    @BeforeEach
    void setUp() {
        listener = new TripEventsListener(jsonMapper, tripFactService);
    }

    @ParameterizedTest
    @ValueSource(strings = {EventTypes.TRIP_SCHEDULED, EventTypes.TRIP_UPDATED, EventTypes.TRIP_STARTED,
            EventTypes.TRIP_COMPLETED, EventTypes.TRIP_DELETED})
    void tripEvent_isApplied(String eventType) {
        listener.onTripEvent(message(eventType, Map.of("tripId", TRIP_ID.toString(), "status", "SCHEDULED")));

        verify(tripFactService).applyTripEvent(eq(EVENT_ID), eq(eventType), payloadCaptor.capture());
        assertEquals(TRIP_ID, payloadCaptor.getValue().tripId());
        assertEquals("SCHEDULED", payloadCaptor.getValue().status());
    }

    @Test
    void scheduledPayload_convertsLocalDepartureAndPrices() {
        listener.onTripEvent(message(EventTypes.TRIP_SCHEDULED, Map.of("tripId", TRIP_ID.toString(),
                "type", "REGULAR", "busId", BUS_ID.toString(), "ticketPrice", 50,
                "departureAt", "2026-11-02T08:30:00", "busPlate", "P-123", "seatLayout", Map.of("rows", 10))));

        verify(tripFactService).applyTripEvent(eq(EVENT_ID), eq(EventTypes.TRIP_SCHEDULED), payloadCaptor.capture());
        TripEventPayload payload = payloadCaptor.getValue();
        assertEquals(LocalDateTime.of(2026, 11, 2, 8, 30), payload.departureAt());
        assertEquals(0, new BigDecimal("50").compareTo(payload.ticketPrice()));
        assertEquals(BUS_ID, payload.busId());
        assertNull(payload.actualArrivalAt());
    }

    @Test
    void completedPayload_convertsInstantsAndAmounts() {
        listener.onTripEvent(message(EventTypes.TRIP_COMPLETED, Map.of("tripId", TRIP_ID.toString(),
                "type", "PRIVATE", "rentalId", RENTAL_ID.toString(), "actualDepartureAt", "2026-10-29T14:00:00Z",
                "actualArrivalAt", "2026-10-29T18:00:00Z", "kmTraveled", 120.5, "fuelCost", 300,
                "driverPay", 150.25, "odometerEnd", 1620.5)));

        verify(tripFactService).applyTripEvent(eq(EVENT_ID), eq(EventTypes.TRIP_COMPLETED), payloadCaptor.capture());
        TripEventPayload payload = payloadCaptor.getValue();
        assertEquals(RENTAL_ID, payload.rentalId());
        assertEquals(Instant.parse("2026-10-29T18:00:00Z"), payload.actualArrivalAt());
        assertEquals(0, new BigDecimal("120.5").compareTo(payload.kmTraveled()));
        assertEquals(0, new BigDecimal("300").compareTo(payload.fuelCost()));
        assertEquals(0, new BigDecimal("150.25").compareTo(payload.driverPay()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PrivateTripRejected", "RentalAssigned", "TripCancelled"})
    void otherEvent_isIgnored(String eventType) {
        listener.onTripEvent(message(eventType, Map.of("tripId", TRIP_ID.toString())));

        verifyNoInteractions(tripFactService);
    }

    @Test
    void eventWithoutType_isIgnored() {
        listener.onTripEvent(jsonMapper.writeValueAsString(Map.of("eventId", EVENT_ID.toString(),
                "payload", Map.of("tripId", TRIP_ID.toString()))));

        verifyNoInteractions(tripFactService);
    }

    @Test
    void malformedMessage_throwsSoItGoesToRetryAndDlt() {
        assertThrows(JacksonException.class, () -> listener.onTripEvent("{no es json"));

        verifyNoInteractions(tripFactService);
    }

    private String message(String eventType, Map<String, Object> payload) {
        return jsonMapper.writeValueAsString(Map.of(
                "eventId", EVENT_ID.toString(),
                "eventType", eventType,
                "occurredAt", "2026-10-29T12:00:00Z",
                "aggregateId", TRIP_ID.toString(),
                "version", 1,
                "payload", payload));
    }
}
