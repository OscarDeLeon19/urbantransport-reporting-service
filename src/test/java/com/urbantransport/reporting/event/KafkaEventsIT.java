package com.urbantransport.reporting.event;

import com.urbantransport.reporting.AbstractIntegrationTest;
import com.urbantransport.reporting.domain.DimBranchEntity;
import com.urbantransport.reporting.domain.DimBusEntity;
import com.urbantransport.reporting.domain.DimDriverEntity;
import com.urbantransport.reporting.domain.DimRouteEntity;
import com.urbantransport.reporting.domain.FactRentalEntity;
import com.urbantransport.reporting.domain.FactTicketSaleEntity;
import com.urbantransport.reporting.domain.FactTripEntity;
import com.urbantransport.reporting.domain.LedgerConcept;
import com.urbantransport.reporting.domain.LedgerEntryEntity;
import com.urbantransport.reporting.domain.LedgerSourceType;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaEventsIT extends AbstractIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Autowired
    private JsonMapper jsonMapper;

    @Value("${app.kafka.topic.identity-events}")
    private String identityTopic;

    @Value("${app.kafka.topic.fleet-events}")
    private String fleetTopic;

    @Value("${app.kafka.topic.trip-events}")
    private String tripTopic;

    @Value("${app.kafka.topic.ticket-events}")
    private String ticketTopic;

    @Value("${app.kafka.topic.rental-events}")
    private String rentalTopic;

    @Value("${app.kafka.topic.finance-events}")
    private String financeTopic;

    @Test
    void branchEvents_areProjectedAndDuplicatedEventIsIgnored() throws Exception {
        UUID branchId = UUID.randomUUID();
        UUID createdEventId = UUID.randomUUID();

        send(identityTopic, branchId, envelope(createdEventId, EventTypes.BRANCH_CREATED, branch(branchId, "Xela", true)));
        send(identityTopic, branchId, envelope(createdEventId, EventTypes.BRANCH_CREATED,
                branch(branchId, "Duplicado", true)));
        send(identityTopic, branchId, envelope(UUID.randomUUID(), "UserCreated", Map.of("userId", "x")));
        send(identityTopic, branchId, envelope(UUID.randomUUID(), EventTypes.BRANCH_UPDATED,
                branch(branchId, "Xela", false)));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            Optional<DimBranchEntity> branch = dimBranchRepository.findById(branchId);
            assertTrue(branch.isPresent());
            assertFalse(branch.get().getActive());
            assertEquals("Xela", branch.get().getName());
            assertEquals(0, new BigDecimal("14.834700").compareTo(branch.get().getLatitude()));
        });
        assertEquals(1, count("SELECT COUNT(*) FROM processed_events WHERE event_id = ?", createdEventId));
        assertEquals(2, count("SELECT COUNT(*) FROM processed_events"));
    }

    @Test
    void fleetEvents_projectBusDriverAndDeletedRoute() throws Exception {
        UUID branchId = UUID.randomUUID();
        UUID busId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID routeId = UUID.randomUUID();
        LocalDate expiresOn = today().plusYears(1);

        send(fleetTopic, busId, envelope(UUID.randomUUID(), EventTypes.BUS_REGISTERED, bus(busId, branchId, "DISPONIBLE")));
        send(fleetTopic, busId, envelope(UUID.randomUUID(), EventTypes.BUS_DEACTIVATED, bus(busId, branchId, "INACTIVO")));
        send(fleetTopic, driverId, envelope(UUID.randomUUID(), EventTypes.DRIVER_REGISTERED, Map.of(
                "driverId", driverId.toString(), "userId", UUID.randomUUID().toString(),
                "branchId", branchId.toString(), "fullName", "Juan Perez", "licenseNumber", "A-123",
                "licenseType", "A", "licenseExpiresOn", expiresOn.toString(), "active", true,
                "baseSalaryPerTrip", 150)));
        send(fleetTopic, routeId, envelope(UUID.randomUUID(), EventTypes.ROUTE_CREATED, route(routeId, branchId, false)));
        send(fleetTopic, routeId, envelope(UUID.randomUUID(), EventTypes.ROUTE_DELETED, route(routeId, branchId, false)));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            Optional<DimBusEntity> bus = dimBusRepository.findById(busId);
            assertTrue(bus.isPresent());
            assertEquals("INACTIVO", bus.get().getStatus());
            assertEquals(branchId, bus.get().getBranchId());
            Optional<DimDriverEntity> driver = dimDriverRepository.findById(driverId);
            assertTrue(driver.isPresent());
            assertEquals(expiresOn, driver.get().getLicenseExpiresOn());
            Optional<DimRouteEntity> route = dimRouteRepository.findById(routeId);
            assertTrue(route.isPresent());
            assertTrue(route.get().getDeleted());
        });
    }

    @Test
    void tripLifecycle_syncsEarlierSalesAndRecordsExpensesInBusBranch() throws Exception {
        UUID branchId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        UUID busId = UUID.randomUUID();
        UUID routeId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        LocalDate departureDay = today().minusDays(1);

        send(fleetTopic, busId, envelope(UUID.randomUUID(), EventTypes.BUS_REGISTERED, bus(busId, branchId, "DISPONIBLE")));
        send(ticketTopic, orderId, envelope(UUID.randomUUID(), EventTypes.TICKET_PURCHASED, Map.of(
                "orderId", orderId.toString(), "channel", "ONLINE", "branchId", branchId.toString(),
                "paymentDate", departureDay.toString(), "total", 50,
                "trip", Map.of("tripId", tripId.toString(), "originName", "Xela", "destinationName", "Guatemala"),
                "tickets", List.of(Map.of("ticketId", ticketId.toString(), "seatNumber", 1, "seatLabel", "1A",
                        "pricePaid", 50)))));
        await().atMost(TIMEOUT).untilAsserted(() -> {
            assertTrue(factTicketSaleRepository.findById(ticketId).isPresent());
            assertTrue(dimBusRepository.findById(busId).isPresent());
        });
        assertNull(factTicketSaleRepository.findById(ticketId).orElseThrow().getRouteId());

        Map<String, Object> scheduled = new HashMap<>(Map.of("tripId", tripId.toString(), "type", "REGULAR",
                "routeId", routeId.toString(), "originBranchId", branchId.toString(),
                "destinationBranchId", destinationId.toString(), "busId", busId.toString(), "ticketPrice", 50,
                "departureAt", departureDay.atTime(8, 0).toString(), "status", "SCHEDULED"));
        send(tripTopic, tripId, envelope(UUID.randomUUID(), EventTypes.TRIP_SCHEDULED, scheduled));
        send(tripTopic, tripId, envelope(UUID.randomUUID(), EventTypes.TRIP_STARTED, Map.of("tripId", tripId.toString(),
                "busId", busId.toString(), "actualDepartureAt", at(departureDay, 8).toString(),
                "status", "IN_TRANSIT")));
        send(tripTopic, tripId, envelope(UUID.randomUUID(), EventTypes.TRIP_COMPLETED, completed(tripId, busId,
                at(departureDay, 12))));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            Optional<FactTripEntity> trip = factTripRepository.findById(tripId);
            assertTrue(trip.isPresent());
            assertEquals("COMPLETED", trip.get().getStatus());
            assertEquals(departureDay.atTime(8, 0).atZone(ZONE).toInstant(), trip.get().getScheduledDeparture());
            assertEquals(0, new BigDecimal("50.00").compareTo(trip.get().getTicketPrice()));
            assertEquals(0, new BigDecimal("120.50").compareTo(trip.get().getKmTraveled()));
            assertEquals(2, count("SELECT COUNT(*) FROM ledger_entries WHERE source_id = ?", tripId));
        });
        FactTicketSaleEntity sale = factTicketSaleRepository.findById(ticketId).orElseThrow();
        assertEquals(routeId, sale.getRouteId());
        assertEquals(busId, sale.getBusId());
        LedgerEntryEntity fuel = ledgerEntry(LedgerSourceType.TRIP_FUEL, tripId);
        assertEquals(LedgerConcept.FUEL, fuel.getConcept());
        assertEquals(branchId, fuel.getBranchId());
        assertEquals(0, new BigDecimal("300.00").compareTo(fuel.getAmount()));
        assertEquals(departureDay, fuel.getOccurredOn());
        assertEquals(0, new BigDecimal("150.00").compareTo(
                ledgerEntry(LedgerSourceType.TRIP_DRIVER_PAY, tripId).getAmount()));
        LedgerEntryEntity income = ledgerEntry(LedgerSourceType.TICKET_SALE, orderId);
        assertEquals(branchId, income.getBranchId());
        assertEquals(0, new BigDecimal("50.00").compareTo(income.getAmount()));
    }

    @Test
    void expensesBeforeTheirBus_getTheBusBranchWhenBusArrives() throws Exception {
        UUID branchId = UUID.randomUUID();
        UUID busId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();

        send(tripTopic, tripId, envelope(UUID.randomUUID(), EventTypes.TRIP_COMPLETED, completed(tripId, busId,
                Instant.now())));
        send(financeTopic, tripId, envelope(UUID.randomUUID(), EventTypes.DEPRECIATION_CALCULATED, depreciation(tripId,
                busId, UUID.randomUUID())));
        await().atMost(TIMEOUT).untilAsserted(() ->
                assertEquals(3, count("SELECT COUNT(*) FROM ledger_entries WHERE source_id = ? AND branch_id IS NULL",
                        tripId)));

        send(fleetTopic, busId, envelope(UUID.randomUUID(), EventTypes.BUS_REGISTERED, bus(busId, branchId, "DISPONIBLE")));

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertEquals(3, count("SELECT COUNT(*) FROM ledger_entries WHERE source_id = ? AND branch_id = ?",
                        tripId, branchId)));
    }

    @Test
    void ledger_isIdempotentBySourceEvenWithDifferentEventIds() throws Exception {
        UUID tripId = UUID.randomUUID();
        UUID busId = UUID.randomUUID();
        Map<String, Object> payload = completed(tripId, busId, Instant.now());

        send(tripTopic, tripId, envelope(UUID.randomUUID(), EventTypes.TRIP_COMPLETED, payload));
        send(tripTopic, tripId, envelope(UUID.randomUUID(), EventTypes.TRIP_COMPLETED, payload));

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertEquals(2, count("SELECT COUNT(*) FROM processed_events WHERE event_type = ?",
                        EventTypes.TRIP_COMPLETED)));
        assertEquals(1, count("SELECT COUNT(*) FROM ledger_entries WHERE source_type = 'TRIP_FUEL' AND source_id = ?",
                tripId));
        assertEquals(1, count("SELECT COUNT(*) FROM ledger_entries WHERE source_type = 'TRIP_DRIVER_PAY' "
                + "AND source_id = ?", tripId));
    }

    @Test
    void ticketCancelled_marksSaleAndRecordsNegativeIncomeOnce() throws Exception {
        UUID branchId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Map<String, Object> payload = Map.of("ticketId", ticketId.toString(), "orderId", UUID.randomUUID().toString(),
                "tripId", UUID.randomUUID().toString(), "seatNumber", 3, "seatLabel", "1C", "channel", "COUNTER",
                "branchId", branchId.toString(), "pricePaid", 50, "refundMethod", "CASH");

        send(ticketTopic, ticketId, envelope(eventId, EventTypes.TICKET_CANCELLED, payload));
        send(ticketTopic, ticketId, envelope(eventId, EventTypes.TICKET_CANCELLED, payload));
        send(ticketTopic, ticketId, envelope(UUID.randomUUID(), "RefundRequested", Map.of("ticketId", ticketId.toString())));
        send(ticketTopic, ticketId, envelope(UUID.randomUUID(), EventTypes.TICKET_CANCELLED, payload));

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertEquals(2, count("SELECT COUNT(*) FROM processed_events WHERE event_type = ?",
                        EventTypes.TICKET_CANCELLED)));
        FactTicketSaleEntity sale = factTicketSaleRepository.findById(ticketId).orElseThrow();
        assertTrue(sale.isCancelled());
        LedgerEntryEntity entry = ledgerEntry(LedgerSourceType.TICKET_CANCELLATION, ticketId);
        assertEquals(0, new BigDecimal("-50.00").compareTo(entry.getAmount()));
        assertEquals(branchId, entry.getBranchId());
        assertEquals(1, count("SELECT COUNT(*) FROM ledger_entries"));
    }

    @Test
    void rentalPaidAndAssigned_buildRentalFactAndIncome() throws Exception {
        UUID rentalId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID busId = UUID.randomUUID();
        LocalDate paymentDate = today().minusDays(1);

        send(rentalTopic, rentalId, envelope(UUID.randomUUID(), EventTypes.RENTAL_PAID, Map.ofEntries(
                Map.entry("rentalId", rentalId.toString()), Map.entry("channel", "ONLINE"),
                Map.entry("branchId", branchId.toString()), Map.entry("customerNit", "1234567"),
                Map.entry("originText", "Xela"), Map.entry("destinationText", "Tikal"),
                Map.entry("departureDate", today().plusDays(5).toString()), Map.entry("passengers", 30),
                Map.entry("distanceKm", 500), Map.entry("confirmedPrice", 1000),
                Map.entry("paymentDate", paymentDate.toString()), Map.entry("status", "PAID"))));
        send(rentalTopic, rentalId, envelope(UUID.randomUUID(), EventTypes.RENTAL_ASSIGNED, Map.of(
                "rentalId", rentalId.toString(), "branchId", branchId.toString(), "busId", busId.toString(),
                "driverId", UUID.randomUUID().toString(), "legs", List.of())));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            Optional<FactRentalEntity> rental = factRentalRepository.findById(rentalId);
            assertTrue(rental.isPresent());
            assertEquals(busId, rental.get().getBusId());
            assertEquals("ASSIGNED", rental.get().getStatus());
        });
        FactRentalEntity rental = factRentalRepository.findById(rentalId).orElseThrow();
        assertEquals("Tikal", rental.getDestinationText());
        assertEquals(0, new BigDecimal("1000.00").compareTo(rental.getAmount()));
        LedgerEntryEntity income = ledgerEntry(LedgerSourceType.RENTAL, rentalId);
        assertEquals(LedgerConcept.RENTAL, income.getConcept());
        assertEquals(paymentDate, income.getOccurredOn());
        assertEquals(branchId, income.getBranchId());
    }

    @Test
    void financeEvents_buildWorkshopDepreciationAndRateFacts() throws Exception {
        UUID branchId = UUID.randomUUID();
        UUID busId = UUID.randomUUID();
        UUID expenseId = UUID.randomUUID();
        UUID laborLine = UUID.randomUUID();
        UUID partLine = UUID.randomUUID();
        UUID rateId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();

        send(fleetTopic, busId, envelope(UUID.randomUUID(), EventTypes.BUS_REGISTERED, bus(busId, branchId, "DISPONIBLE")));
        await().atMost(TIMEOUT).untilAsserted(() -> assertTrue(dimBusRepository.findById(busId).isPresent()));
        send(financeTopic, expenseId, envelope(UUID.randomUUID(), EventTypes.WORKSHOP_EXPENSE_REGISTERED, Map.of(
                "expenseId", expenseId.toString(), "busId", busId.toString(), "branchId", branchId.toString(),
                "serviceDate", today().toString(), "total", 500,
                "lines", List.of(
                        Map.of("lineId", laborLine.toString(), "categoryId", UUID.randomUUID().toString(),
                                "categoryName", "Motor", "description", "Ajuste", "type", "LABOR", "amount", 150),
                        Map.of("lineId", partLine.toString(), "categoryId", UUID.randomUUID().toString(),
                                "categoryName", "Motor", "type", "PART", "amount", 350)))));
        send(financeTopic, rateId, envelope(UUID.randomUUID(), EventTypes.DEPRECIATION_RATE_CHANGED, Map.of(
                "rateId", rateId.toString(), "amountPerKm", 0.75, "effectiveFrom", Instant.now().toString())));
        send(financeTopic, tripId, envelope(UUID.randomUUID(), EventTypes.DEPRECIATION_CALCULATED, depreciation(tripId,
                busId, UUID.randomUUID())));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            assertEquals(2, count("SELECT COUNT(*) FROM fact_workshop_line WHERE expense_id = ?", expenseId));
            assertTrue(dimDepreciationRateRepository.findById(rateId).isPresent());
            assertTrue(factDepreciationRepository.findById(tripId).isPresent());
        });
        assertEquals(LedgerConcept.WORKSHOP, ledgerEntry(LedgerSourceType.WORKSHOP_LINE, laborLine).getConcept());
        assertEquals(LedgerConcept.SPARE_PARTS, ledgerEntry(LedgerSourceType.WORKSHOP_LINE, partLine).getConcept());
        LedgerEntryEntity depreciation = ledgerEntry(LedgerSourceType.DEPRECIATION, tripId);
        assertEquals(branchId, depreciation.getBranchId());
        assertEquals(0, new BigDecimal("90.38").compareTo(depreciation.getAmount()));
    }

    @Test
    void malformedTripEvent_endsInDeadLetterTopic() throws Exception {
        UUID key = UUID.randomUUID();

        send(tripTopic, key, "{no es json");

        ConsumerRecord<String, String> record = awaitRecord(tripTopic + ".DLT", key.toString());
        assertEquals("{no es json", record.value());
        assertEquals(0, count("SELECT COUNT(*) FROM processed_events"));
    }

    private LedgerEntryEntity ledgerEntry(LedgerSourceType sourceType, UUID sourceId) {
        return ledgerEntryRepository.findAll().stream()
                .filter(entry -> entry.getSourceType() == sourceType && entry.getSourceId().equals(sourceId))
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, Object> branch(UUID branchId, String name, boolean active) {
        return Map.of("branchId", branchId.toString(), "name", name, "code", "XEL", "latitude", 14.8347,
                "longitude", -91.518, "active", active);
    }

    private static Map<String, Object> bus(UUID busId, UUID branchId, String status) {
        return Map.of("busId", busId.toString(), "branchId", branchId.toString(), "plate", "P-123ABC",
                "brand", "Volvo", "model", "B8R", "manufactureYear", 2020, "capacity", 40, "status", status,
                "odometerKm", 1500, "seatRows", 10);
    }

    private static Map<String, Object> route(UUID routeId, UUID originId, boolean deleted) {
        return Map.of("routeId", routeId.toString(), "originBranchId", originId.toString(),
                "destinationBranchId", UUID.randomUUID().toString(), "distanceKm", 120.5, "ticketPrice", 50,
                "deleted", deleted);
    }

    private static Map<String, Object> completed(UUID tripId, UUID busId, Instant arrivedAt) {
        return Map.of("tripId", tripId.toString(), "type", "REGULAR", "busId", busId.toString(),
                "actualArrivalAt", arrivedAt.toString(), "kmTraveled", 120.5, "fuelCost", 300, "driverPay", 150,
                "odometerEnd", 1620.5, "status", "COMPLETED");
    }

    private static Map<String, Object> depreciation(UUID tripId, UUID busId, UUID tripBranchId) {
        return Map.of("tripId", tripId.toString(), "busId", busId.toString(), "branchId", tripBranchId.toString(),
                "km", 120.5, "rateId", UUID.randomUUID().toString(), "rateApplied", 0.75, "amount", 90.38,
                "arrivedAt", Instant.now().toString());
    }

    private void send(String topic, UUID key, String value) throws Exception {
        kafkaTemplate.send(topic, key.toString(), value).get(10, TimeUnit.SECONDS);
    }

    private String envelope(UUID eventId, String eventType, Map<String, Object> payload) {
        return jsonMapper.writeValueAsString(Map.of(
                "eventId", eventId.toString(),
                "eventType", eventType,
                "occurredAt", Instant.now().toString(),
                "aggregateId", UUID.randomUUID().toString(),
                "version", 1,
                "correlationId", UUID.randomUUID().toString(),
                "payload", payload));
    }

    private ConsumerRecord<String, String> awaitRecord(String topic, String key) {
        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        try (Consumer<String, String> consumer = consumerFactory.createConsumer("it-" + UUID.randomUUID(), "it")) {
            consumer.subscribe(List.of(topic));
            await().atMost(Duration.ofSeconds(90)).pollInSameThread().untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(received::add);
                assertTrue(received.stream().anyMatch(record -> key.equals(record.key())),
                        "no llegó el mensaje con key " + key + " a " + topic);
            });
        }
        return received.stream().filter(record -> key.equals(record.key())).findFirst().orElseThrow();
    }
}
