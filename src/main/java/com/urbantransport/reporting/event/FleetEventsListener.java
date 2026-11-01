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
public class FleetEventsListener {

    private final JsonMapper jsonMapper;
    private final DimensionService dimensionService;

    @KafkaListener(topics = "${app.kafka.topic.fleet-events}")
    public void onFleetEvent(String message) {
        IncomingEvent event = jsonMapper.readValue(message, IncomingEvent.class);
        String eventType = event.eventType() == null ? "" : event.eventType();

        switch (eventType) {
            case EventTypes.BUS_REGISTERED, EventTypes.BUS_UPDATED, EventTypes.BUS_DEACTIVATED ->
                    dimensionService.applyBus(event.eventId(), eventType,
                            jsonMapper.convertValue(event.payload(), BusEventPayload.class));
            case EventTypes.DRIVER_REGISTERED, EventTypes.DRIVER_UPDATED, EventTypes.DRIVER_DEACTIVATED ->
                    dimensionService.applyDriver(event.eventId(), eventType,
                            jsonMapper.convertValue(event.payload(), DriverEventPayload.class));
            case EventTypes.ROUTE_CREATED, EventTypes.ROUTE_UPDATED, EventTypes.ROUTE_DELETED ->
                    dimensionService.applyRoute(event.eventId(), eventType,
                            jsonMapper.convertValue(event.payload(), RouteEventPayload.class));
            default -> log.debug("Evento {} ignorado", eventType);
        }
    }
}
