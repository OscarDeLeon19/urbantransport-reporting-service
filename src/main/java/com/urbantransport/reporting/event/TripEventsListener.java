package com.urbantransport.reporting.event;

import com.urbantransport.reporting.service.TripFactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class TripEventsListener {

    private final JsonMapper jsonMapper;
    private final TripFactService tripFactService;

    @KafkaListener(topics = "${app.kafka.topic.trip-events}")
    public void onTripEvent(String message) {
        IncomingEvent event = jsonMapper.readValue(message, IncomingEvent.class);
        String eventType = event.eventType() == null ? "" : event.eventType();

        switch (eventType) {
            case EventTypes.TRIP_SCHEDULED, EventTypes.TRIP_UPDATED, EventTypes.TRIP_STARTED,
                 EventTypes.TRIP_COMPLETED, EventTypes.TRIP_DELETED -> tripFactService.applyTripEvent(
                    event.eventId(), eventType, jsonMapper.convertValue(event.payload(), TripEventPayload.class));
            default -> log.debug("Evento {} ignorado", eventType);
        }
    }
}
