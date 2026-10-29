package com.urbantransport.reporting.service;

import com.urbantransport.reporting.event.RentalAssignedPayload;
import com.urbantransport.reporting.event.RentalEventPayload;

import java.time.Instant;
import java.util.UUID;

public interface RentalFactService {

    void applyRentalPaid(UUID eventId, String eventType, Instant occurredAt, RentalEventPayload payload);

    void applyRentalAssigned(UUID eventId, String eventType, RentalAssignedPayload payload);
}
