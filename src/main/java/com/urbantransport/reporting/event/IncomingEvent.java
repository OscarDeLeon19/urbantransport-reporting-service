package com.urbantransport.reporting.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IncomingEvent(UUID eventId, String eventType, Instant occurredAt, Map<String, Object> payload) {
}
