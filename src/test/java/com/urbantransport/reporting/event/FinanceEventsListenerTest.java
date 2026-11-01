package com.urbantransport.reporting.event;

import com.urbantransport.reporting.service.FinanceFactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class FinanceEventsListenerTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EXPENSE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BUS_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID BRANCH_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID LINE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID CATEGORY_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID RATE_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID TRIP_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private FinanceFactService financeFactService;

    private FinanceEventsListener listener;

    @BeforeEach
    void setUp() {
        listener = new FinanceEventsListener(jsonMapper, financeFactService);
    }

    @Test
    void workshopExpense_isAppliedWithLines() {
        listener.onFinanceEvent(message(EventTypes.WORKSHOP_EXPENSE_REGISTERED, Map.of(
                "expenseId", EXPENSE_ID.toString(), "busId", BUS_ID.toString(), "branchId", BRANCH_ID.toString(),
                "serviceDate", "2026-10-28", "total", 300, "createdBy", UUID.randomUUID().toString(),
                "lines", List.of(Map.of("lineId", LINE_ID.toString(), "categoryId", CATEGORY_ID.toString(),
                        "categoryName", "Motor", "description", "Ajuste", "type", "PART", "amount", 300)))));

        ArgumentCaptor<WorkshopExpenseRegisteredPayload> captor =
                ArgumentCaptor.forClass(WorkshopExpenseRegisteredPayload.class);
        verify(financeFactService).applyWorkshopExpense(eq(EVENT_ID), eq(EventTypes.WORKSHOP_EXPENSE_REGISTERED),
                captor.capture());
        WorkshopExpenseRegisteredPayload payload = captor.getValue();
        assertEquals(EXPENSE_ID, payload.expenseId());
        assertEquals(LocalDate.of(2026, 10, 28), payload.serviceDate());
        assertEquals(LINE_ID, payload.lines().getFirst().lineId());
        assertEquals("PART", payload.lines().getFirst().type());
        assertEquals(0, new BigDecimal("300").compareTo(payload.lines().getFirst().amount()));
        verifyNoMoreInteractions(financeFactService);
    }

    @Test
    void rateChanged_isAppliedWithConvertedPayload() {
        listener.onFinanceEvent(message(EventTypes.DEPRECIATION_RATE_CHANGED, Map.of("rateId", RATE_ID.toString(),
                "amountPerKm", 0.75, "effectiveFrom", "2026-10-01T06:00:00Z",
                "createdBy", UUID.randomUUID().toString())));

        ArgumentCaptor<DepreciationRateChangedPayload> captor =
                ArgumentCaptor.forClass(DepreciationRateChangedPayload.class);
        verify(financeFactService).applyDepreciationRateChanged(eq(EVENT_ID),
                eq(EventTypes.DEPRECIATION_RATE_CHANGED), captor.capture());
        assertEquals(RATE_ID, captor.getValue().rateId());
        assertEquals(0, new BigDecimal("0.75").compareTo(captor.getValue().amountPerKm()));
        assertEquals(Instant.parse("2026-10-01T06:00:00Z"), captor.getValue().effectiveFrom());
        verifyNoMoreInteractions(financeFactService);
    }

    @Test
    void depreciationCalculated_isAppliedWithConvertedPayload() {
        listener.onFinanceEvent(message(EventTypes.DEPRECIATION_CALCULATED, Map.of("tripId", TRIP_ID.toString(),
                "busId", BUS_ID.toString(), "branchId", BRANCH_ID.toString(), "km", 120.5,
                "rateId", RATE_ID.toString(), "rateApplied", 0.75, "amount", 90.38,
                "arrivedAt", "2026-10-29T18:00:00Z")));

        ArgumentCaptor<DepreciationCalculatedPayload> captor =
                ArgumentCaptor.forClass(DepreciationCalculatedPayload.class);
        verify(financeFactService).applyDepreciationCalculated(eq(EVENT_ID),
                eq(EventTypes.DEPRECIATION_CALCULATED), captor.capture());
        DepreciationCalculatedPayload payload = captor.getValue();
        assertEquals(TRIP_ID, payload.tripId());
        assertEquals(0, new BigDecimal("120.5").compareTo(payload.km()));
        assertEquals(0, new BigDecimal("90.38").compareTo(payload.amount()));
        assertEquals(Instant.parse("2026-10-29T18:00:00Z"), payload.arrivedAt());
        verifyNoMoreInteractions(financeFactService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ExpenseCategoryCreated", "TripCompleted"})
    void otherEvent_isIgnored(String eventType) {
        listener.onFinanceEvent(message(eventType, Map.of("tripId", TRIP_ID.toString())));

        verifyNoInteractions(financeFactService);
    }

    @Test
    void eventWithoutType_isIgnored() {
        listener.onFinanceEvent(jsonMapper.writeValueAsString(Map.of("eventId", EVENT_ID.toString(),
                "payload", Map.of("tripId", TRIP_ID.toString()))));

        verifyNoInteractions(financeFactService);
    }

    @Test
    void malformedMessage_throwsSoItGoesToRetryAndDlt() {
        assertThrows(JacksonException.class, () -> listener.onFinanceEvent("{no es json"));

        verifyNoInteractions(financeFactService);
    }

    private String message(String eventType, Map<String, Object> payload) {
        return jsonMapper.writeValueAsString(Map.of(
                "eventId", EVENT_ID.toString(),
                "eventType", eventType,
                "occurredAt", "2026-10-29T12:00:00Z",
                "aggregateId", UUID.randomUUID().toString(),
                "version", 1,
                "payload", payload));
    }
}
