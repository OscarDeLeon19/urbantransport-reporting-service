package com.urbantransport.reporting.event;

import com.urbantransport.reporting.service.TicketFactService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class TicketEventsListenerTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TRIP_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID TICKET_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID BRANCH_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-29T12:00:00Z");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private TicketFactService ticketFactService;

    private TicketEventsListener listener;

    @BeforeEach
    void setUp() {
        listener = new TicketEventsListener(jsonMapper, ticketFactService);
    }

    @Test
    void ticketPurchased_isAppliedWithTripAndTickets() {
        listener.onTicketEvent(message(EventTypes.TICKET_PURCHASED, Map.of("orderId", ORDER_ID.toString(),
                "channel", "ONLINE", "branchId", BRANCH_ID.toString(), "paymentDate", "2026-10-29", "total", 100,
                "buyerEmail", "a@b.com",
                "trip", Map.of("tripId", TRIP_ID.toString(), "originName", "Xela", "destinationName", "Guate",
                        "busPlate", "P-1"),
                "tickets", List.of(Map.of("ticketId", TICKET_ID.toString(), "seatNumber", 3, "seatLabel", "1C",
                        "pricePaid", 50.5)))));

        ArgumentCaptor<TicketPurchasedPayload> captor = ArgumentCaptor.forClass(TicketPurchasedPayload.class);
        verify(ticketFactService).applyTicketPurchased(eq(EVENT_ID), eq(EventTypes.TICKET_PURCHASED),
                eq(OCCURRED_AT), captor.capture());
        TicketPurchasedPayload payload = captor.getValue();
        assertEquals(ORDER_ID, payload.orderId());
        assertEquals(LocalDate.of(2026, 10, 29), payload.paymentDate());
        assertEquals(TRIP_ID, payload.trip().tripId());
        assertEquals("Xela", payload.trip().originName());
        assertEquals(TICKET_ID, payload.tickets().getFirst().ticketId());
        assertEquals(3, payload.tickets().getFirst().seatNumber());
        assertEquals(0, new BigDecimal("50.5").compareTo(payload.tickets().getFirst().pricePaid()));
        verifyNoMoreInteractions(ticketFactService);
    }

    @Test
    void ticketCancelled_isAppliedWithOccurredAt() {
        listener.onTicketEvent(message(EventTypes.TICKET_CANCELLED, Map.of("ticketId", TICKET_ID.toString(),
                "orderId", ORDER_ID.toString(), "tripId", TRIP_ID.toString(), "seatNumber", 3, "seatLabel", "1C",
                "channel", "COUNTER", "branchId", BRANCH_ID.toString(), "pricePaid", 50, "refundMethod", "CASH",
                "reason", "TRIP_DELETED")));

        ArgumentCaptor<TicketCancelledPayload> captor = ArgumentCaptor.forClass(TicketCancelledPayload.class);
        verify(ticketFactService).applyTicketCancelled(eq(EVENT_ID), eq(EventTypes.TICKET_CANCELLED),
                eq(OCCURRED_AT), captor.capture());
        TicketCancelledPayload payload = captor.getValue();
        assertEquals(TICKET_ID, payload.ticketId());
        assertEquals("1C", payload.seatLabel());
        assertEquals("CASH", payload.refundMethod());
        assertEquals(0, new BigDecimal("50").compareTo(payload.pricePaid()));
        verifyNoMoreInteractions(ticketFactService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PaymentRequested", "RefundRequested", "TicketReserved"})
    void otherEvent_isIgnored(String eventType) {
        listener.onTicketEvent(message(eventType, Map.of("orderId", ORDER_ID.toString())));

        verifyNoInteractions(ticketFactService);
    }

    @Test
    void eventWithoutType_isIgnored() {
        listener.onTicketEvent(jsonMapper.writeValueAsString(Map.of("eventId", EVENT_ID.toString(),
                "payload", Map.of("orderId", ORDER_ID.toString()))));

        verifyNoInteractions(ticketFactService);
    }

    @Test
    void malformedMessage_throwsSoItGoesToRetryAndDlt() {
        assertThrows(JacksonException.class, () -> listener.onTicketEvent("{no es json"));

        verifyNoInteractions(ticketFactService);
    }

    private String message(String eventType, Map<String, Object> payload) {
        return jsonMapper.writeValueAsString(Map.of(
                "eventId", EVENT_ID.toString(),
                "eventType", eventType,
                "occurredAt", OCCURRED_AT.toString(),
                "aggregateId", ORDER_ID.toString(),
                "version", 1,
                "payload", payload));
    }
}
