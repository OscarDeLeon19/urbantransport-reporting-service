package com.urbantransport.reporting.service;

import com.urbantransport.reporting.event.DepreciationCalculatedPayload;
import com.urbantransport.reporting.event.DepreciationRateChangedPayload;
import com.urbantransport.reporting.event.WorkshopExpenseRegisteredPayload;

import java.util.UUID;

public interface FinanceFactService {

    void applyWorkshopExpense(UUID eventId, String eventType, WorkshopExpenseRegisteredPayload payload);

    void applyDepreciationRateChanged(UUID eventId, String eventType, DepreciationRateChangedPayload payload);

    void applyDepreciationCalculated(UUID eventId, String eventType, DepreciationCalculatedPayload payload);
}
