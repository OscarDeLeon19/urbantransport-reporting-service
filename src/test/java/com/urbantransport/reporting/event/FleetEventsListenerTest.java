package com.urbantransport.reporting.event;

import com.urbantransport.reporting.service.DimensionService;
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
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class FleetEventsListenerTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRANCH_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BUS_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID DRIVER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID USER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID ROUTE_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID DESTINATION_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private DimensionService dimensionService;

    private FleetEventsListener listener;

    @BeforeEach
    void setUp() {
        listener = new FleetEventsListener(jsonMapper, dimensionService);
    }

    @ParameterizedTest
    @ValueSource(strings = {EventTypes.BUS_REGISTERED, EventTypes.BUS_UPDATED, EventTypes.BUS_DEACTIVATED})
    void busEvent_isAppliedWithConvertedPayload(String eventType) {
        listener.onFleetEvent(message(eventType, Map.of("busId", BUS_ID.toString(), "branchId", BRANCH_ID.toString(),
                "plate", "P-123ABC", "brand", "Volvo", "model", "B8R", "manufactureYear", 2020, "capacity", 40,
                "status", "DISPONIBLE", "odometerKm", 1500, "seatRows", 10)));

        ArgumentCaptor<BusEventPayload> captor = ArgumentCaptor.forClass(BusEventPayload.class);
        verify(dimensionService).applyBus(eq(EVENT_ID), eq(eventType), captor.capture());
        BusEventPayload payload = captor.getValue();
        assertEquals(BUS_ID, payload.busId());
        assertEquals(BRANCH_ID, payload.branchId());
        assertEquals("P-123ABC", payload.plate());
        assertEquals((short) 2020, payload.manufactureYear());
        assertEquals(40, payload.capacity());
        assertEquals("DISPONIBLE", payload.status());
        assertEquals(0, new BigDecimal("1500").compareTo(payload.odometerKm()));
        verifyNoMoreInteractions(dimensionService);
    }

    @ParameterizedTest
    @ValueSource(strings = {EventTypes.DRIVER_REGISTERED, EventTypes.DRIVER_UPDATED, EventTypes.DRIVER_DEACTIVATED})
    void driverEvent_isAppliedWithConvertedPayload(String eventType) {
        listener.onFleetEvent(message(eventType, Map.of("driverId", DRIVER_ID.toString(), "userId", USER_ID.toString(),
                "branchId", BRANCH_ID.toString(), "fullName", "Juan Pérez", "licenseNumber", "A-123",
                "licenseType", "A", "licenseExpiresOn", "2027-03-01", "active", false, "phone", "5555")));

        ArgumentCaptor<DriverEventPayload> captor = ArgumentCaptor.forClass(DriverEventPayload.class);
        verify(dimensionService).applyDriver(eq(EVENT_ID), eq(eventType), captor.capture());
        DriverEventPayload payload = captor.getValue();
        assertEquals(DRIVER_ID, payload.driverId());
        assertEquals(USER_ID, payload.userId());
        assertEquals("Juan Pérez", payload.fullName());
        assertEquals("A", payload.licenseType());
        assertEquals(LocalDate.of(2027, 3, 1), payload.licenseExpiresOn());
        assertFalse(payload.active());
        verifyNoMoreInteractions(dimensionService);
    }

    @ParameterizedTest
    @ValueSource(strings = {EventTypes.ROUTE_CREATED, EventTypes.ROUTE_UPDATED, EventTypes.ROUTE_DELETED})
    void routeEvent_isAppliedWithConvertedPayload(String eventType) {
        listener.onFleetEvent(message(eventType, Map.of("routeId", ROUTE_ID.toString(),
                "originBranchId", BRANCH_ID.toString(), "destinationBranchId", DESTINATION_ID.toString(),
                "distanceKm", 120.5, "ticketPrice", 45, "deleted", true)));

        ArgumentCaptor<RouteEventPayload> captor = ArgumentCaptor.forClass(RouteEventPayload.class);
        verify(dimensionService).applyRoute(eq(EVENT_ID), eq(eventType), captor.capture());
        RouteEventPayload payload = captor.getValue();
        assertEquals(ROUTE_ID, payload.routeId());
        assertEquals(DESTINATION_ID, payload.destinationBranchId());
        assertEquals(0, new BigDecimal("120.5").compareTo(payload.distanceKm()));
        assertEquals(0, new BigDecimal("45").compareTo(payload.ticketPrice()));
        assertTrue(payload.deleted());
        verifyNoMoreInteractions(dimensionService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TripStarted", "BranchCreated", "BusDeleted"})
    void otherEvent_isIgnored(String eventType) {
        listener.onFleetEvent(message(eventType, Map.of("busId", BUS_ID.toString())));

        verifyNoInteractions(dimensionService);
    }

    @Test
    void eventWithoutType_isIgnored() {
        listener.onFleetEvent(jsonMapper.writeValueAsString(Map.of("eventId", EVENT_ID.toString(),
                "payload", Map.of("busId", BUS_ID.toString()))));

        verifyNoInteractions(dimensionService);
    }

    @Test
    void malformedMessage_throwsSoItGoesToRetryAndDlt() {
        assertThrows(JacksonException.class, () -> listener.onFleetEvent("{no es json"));

        verifyNoInteractions(dimensionService);
    }

    private String message(String eventType, Map<String, Object> payload) {
        return jsonMapper.writeValueAsString(Map.of(
                "eventId", EVENT_ID.toString(),
                "eventType", eventType,
                "occurredAt", "2026-10-29T12:00:00Z",
                "aggregateId", UUID.randomUUID().toString(),
                "version", 1,
                "payload", payload));
    }
}
