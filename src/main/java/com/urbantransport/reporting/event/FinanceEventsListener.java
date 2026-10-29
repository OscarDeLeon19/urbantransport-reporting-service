package com.urbantransport.reporting.event;

import com.urbantransport.reporting.service.FinanceFactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class FinanceEventsListener {

    private final JsonMapper jsonMapper;
    private final FinanceFactService financeFactService;

    @KafkaListener(topics = "${app.kafka.topic.finance-events}")
    public void onFinanceEvent(String message) {
        IncomingEvent event = jsonMapper.readValue(message, IncomingEvent.class);
        String eventType = event.eventType() == null ? "" : event.eventType();

        switch (eventType) {
            case EventTypes.WORKSHOP_EXPENSE_REGISTERED -> financeFactService.applyWorkshopExpense(event.eventId(),
                    eventType, jsonMapper.convertValue(event.payload(), WorkshopExpenseRegisteredPayload.class));
            case EventTypes.DEPRECIATION_RATE_CHANGED -> financeFactService.applyDepreciationRateChanged(
                    event.eventId(), eventType,
                    jsonMapper.convertValue(event.payload(), DepreciationRateChangedPayload.class));
            case EventTypes.DEPRECIATION_CALCULATED -> financeFactService.applyDepreciationCalculated(
                    event.eventId(), eventType,
                    jsonMapper.convertValue(event.payload(), DepreciationCalculatedPayload.class));
            default -> log.debug("Evento {} ignorado", eventType);
        }
    }
}
