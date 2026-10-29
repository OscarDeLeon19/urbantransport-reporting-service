package com.urbantransport.reporting.event;

import com.urbantransport.reporting.service.TicketFactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketEventsListener {

    private final JsonMapper jsonMapper;
    private final TicketFactService ticketFactService;

    @KafkaListener(topics = "${app.kafka.topic.ticket-events}")
    public void onTicketEvent(String message) {
        IncomingEvent event = jsonMapper.readValue(message, IncomingEvent.class);
        String eventType = event.eventType() == null ? "" : event.eventType();

        switch (eventType) {
            case EventTypes.TICKET_PURCHASED -> ticketFactService.applyTicketPurchased(event.eventId(), eventType,
                    event.occurredAt(), jsonMapper.convertValue(event.payload(), TicketPurchasedPayload.class));
            case EventTypes.TICKET_CANCELLED -> ticketFactService.applyTicketCancelled(event.eventId(), eventType,
                    event.occurredAt(), jsonMapper.convertValue(event.payload(), TicketCancelledPayload.class));
            default -> log.debug("Evento {} ignorado", eventType);
        }
    }
}
