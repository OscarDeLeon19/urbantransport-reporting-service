package com.urbantransport.reporting.service;

import com.urbantransport.reporting.event.TripEventPayload;

import java.util.UUID;

public interface TripFactService {

    void applyTripEvent(UUID eventId, String eventType, TripEventPayload payload);
}
