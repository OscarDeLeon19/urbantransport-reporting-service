package com.urbantransport.reporting.service;

import com.urbantransport.reporting.event.TicketCancelledPayload;
import com.urbantransport.reporting.event.TicketPurchasedPayload;

import java.time.Instant;
import java.util.UUID;

public interface TicketFactService {

    void applyTicketPurchased(UUID eventId, String eventType, Instant occurredAt, TicketPurchasedPayload payload);

    void applyTicketCancelled(UUID eventId, String eventType, Instant occurredAt, TicketCancelledPayload payload);
}
