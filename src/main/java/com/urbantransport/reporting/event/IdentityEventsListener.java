package com.urbantransport.reporting.event;

import com.urbantransport.reporting.service.DimensionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityEventsListener {

    private final JsonMapper jsonMapper;
    private final DimensionService dimensionService;

    @KafkaListener(topics = "${app.kafka.topic.identity-events}")
    public void onIdentityEvent(String message) {
        IncomingEvent event = jsonMapper.readValue(message, IncomingEvent.class);
        String eventType = event.eventType() == null ? "" : event.eventType();

        switch (eventType) {
            case EventTypes.BRANCH_CREATED, EventTypes.BRANCH_UPDATED -> dimensionService.applyBranch(
                    event.eventId(), eventType, jsonMapper.convertValue(event.payload(), BranchEventPayload.class));
            default -> log.debug("Evento {} ignorado", eventType);
        }
    }
}
