package com.urbantransport.reporting;

import com.urbantransport.reporting.domain.DimBranchEntity;
import com.urbantransport.reporting.domain.DimBusEntity;
import com.urbantransport.reporting.domain.DimDepreciationRateEntity;
import com.urbantransport.reporting.domain.DimDriverEntity;
import com.urbantransport.reporting.domain.DimRouteEntity;
import com.urbantransport.reporting.domain.FactDepreciationEntity;
import com.urbantransport.reporting.domain.FactRentalEntity;
import com.urbantransport.reporting.domain.FactTicketSaleEntity;
import com.urbantransport.reporting.domain.FactTripEntity;
import com.urbantransport.reporting.domain.FactWorkshopLineEntity;
import com.urbantransport.reporting.domain.LedgerConcept;
import com.urbantransport.reporting.domain.LedgerEntryEntity;
import com.urbantransport.reporting.domain.LedgerEntryType;
import com.urbantransport.reporting.domain.LedgerSourceType;
import com.urbantransport.reporting.repository.DimBranchRepository;
import com.urbantransport.reporting.repository.DimBusRepository;
import com.urbantransport.reporting.repository.DimDepreciationRateRepository;
import com.urbantransport.reporting.repository.DimDriverRepository;
import com.urbantransport.reporting.repository.DimRouteRepository;
import com.urbantransport.reporting.repository.FactDepreciationRepository;
import com.urbantransport.reporting.repository.FactRentalRepository;
import com.urbantransport.reporting.repository.FactTicketSaleRepository;
import com.urbantransport.reporting.repository.FactTripRepository;
import com.urbantransport.reporting.repository.FactWorkshopLineRepository;
import com.urbantransport.reporting.repository.LedgerEntryRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
public abstract class AbstractIntegrationTest {

    protected static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    protected static final UUID USER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private static final EmbeddedPostgres POSTGRES = startPostgres();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected DimBranchRepository dimBranchRepository;

    @Autowired
    protected DimBusRepository dimBusRepository;

    @Autowired
    protected DimDriverRepository dimDriverRepository;

    @Autowired
    protected DimRouteRepository dimRouteRepository;

    @Autowired
    protected DimDepreciationRateRepository dimDepreciationRateRepository;

    @Autowired
    protected FactTripRepository factTripRepository;

    @Autowired
    protected FactTicketSaleRepository factTicketSaleRepository;

    @Autowired
    protected FactRentalRepository factRentalRepository;

    @Autowired
    protected FactWorkshopLineRepository factWorkshopLineRepository;

    @Autowired
    protected FactDepreciationRepository factDepreciationRepository;

    @Autowired
    protected LedgerEntryRepository ledgerEntryRepository;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE dim_branch, dim_bus, dim_driver, dim_route, dim_depreciation_rate, fact_trip, "
                + "fact_ticket_sale, fact_rental, fact_workshop_line, fact_depreciation, ledger_entries, "
                + "processed_events CASCADE");
    }

    protected static RequestPostProcessor branchAdmin(UUID branchId) {
        return userWithRole("BRANCH_ADMIN", branchId);
    }

    protected static RequestPostProcessor systemAdmin() {
        return userWithRole("SYSTEM_ADMIN", null);
    }

    protected static RequestPostProcessor userWithRole(String role, UUID branchId) {
        return jwt()
                .jwt(token -> {
                    token.subject(USER_ID.toString()).claim("roles", List.of(role));
                    if (branchId != null) {
                        token.claim("branchId", branchId.toString());
                    }
                })
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    protected static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    protected static Instant at(LocalDate date, int hour) {
        return date.atTime(hour, 0).atZone(ZONE).toInstant();
    }

    protected UUID saveBranch(String name, String latitude, String longitude) {
        return dimBranchRepository.save(DimBranchEntity.builder()
                .branchId(UUID.randomUUID())
                .name(name)
                .code(name.substring(0, 3).toUpperCase())
                .latitude(latitude == null ? null : new BigDecimal(latitude))
                .longitude(longitude == null ? null : new BigDecimal(longitude))
                .active(true)
                .build()).getBranchId();
    }

    protected UUID saveBus(UUID branchId, String plate, String status) {
        return dimBusRepository.save(DimBusEntity.builder()
                .busId(UUID.randomUUID())
                .branchId(branchId)
                .plate(plate)
                .brand("Volvo")
                .model("B8R")
                .manufactureYear((short) 2020)
                .capacity(40)
                .status(status)
                .odometerKm(new BigDecimal("1500.00"))
                .build()).getBusId();
    }

    protected UUID saveDriver(UUID branchId, String fullName, String licenseNumber, LocalDate expiresOn,
                              boolean active) {
        return dimDriverRepository.save(DimDriverEntity.builder()
                .driverId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .branchId(branchId)
                .fullName(fullName)
                .licenseNumber(licenseNumber)
                .licenseType("A")
                .licenseExpiresOn(expiresOn)
                .active(active)
                .build()).getDriverId();
    }

    protected UUID saveRoute(UUID originId, UUID destinationId, String price, boolean deleted) {
        return dimRouteRepository.save(DimRouteEntity.builder()
                .routeId(UUID.randomUUID())
                .originBranchId(originId)
                .destinationBranchId(destinationId)
                .distanceKm(new BigDecimal("120.50"))
                .ticketPrice(new BigDecimal(price))
                .deleted(deleted)
                .build()).getRouteId();
    }

    protected UUID saveTrip(UUID routeId, UUID originId, UUID destinationId, UUID busId, UUID driverId,
                            String status, Instant scheduledDeparture) {
        return factTripRepository.save(FactTripEntity.builder()
                .tripId(UUID.randomUUID())
                .type("REGULAR")
                .routeId(routeId)
                .originBranchId(originId)
                .destinationBranchId(destinationId)
                .busId(busId)
                .driverId(driverId)
                .scheduledDeparture(scheduledDeparture)
                .ticketPrice(new BigDecimal("50.00"))
                .status(status)
                .build()).getTripId();
    }

    protected void saveSale(UUID tripId, UUID routeId, UUID busId, UUID branchId, String price, LocalDate paymentDate,
                            boolean cancelled) {
        factTicketSaleRepository.save(FactTicketSaleEntity.builder()
                .ticketId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .tripId(tripId)
                .routeId(routeId)
                .busId(busId)
                .branchId(branchId)
                .channel("ONLINE")
                .seatNumber(1)
                .pricePaid(new BigDecimal(price))
                .paymentDate(paymentDate)
                .cancelled(cancelled)
                .build());
    }

    protected void saveLedger(UUID branchId, LedgerEntryType type, LedgerConcept concept, String amount,
                              LocalDate occurredOn, String description) {
        ledgerEntryRepository.save(LedgerEntryEntity.builder()
                .type(type)
                .concept(concept)
                .branchId(branchId)
                .amount(new BigDecimal(amount))
                .occurredOn(occurredOn)
                .sourceType(type == LedgerEntryType.INCOME ? LedgerSourceType.TICKET_SALE : LedgerSourceType.TRIP_FUEL)
                .sourceId(UUID.randomUUID())
                .description(description)
                .build());
    }

    protected void saveRental(UUID branchId, UUID busId, String amount, LocalDate paymentDate) {
        factRentalRepository.save(FactRentalEntity.builder()
                .rentalId(UUID.randomUUID())
                .branchId(branchId)
                .channel("COUNTER")
                .customerNit("CF")
                .originText("Xela")
                .destinationText("Tikal")
                .departureDate(paymentDate.plusDays(5))
                .paymentDate(paymentDate)
                .passengers(30)
                .distanceKm(new BigDecimal("500.00"))
                .amount(amount == null ? null : new BigDecimal(amount))
                .busId(busId)
                .status("PAID")
                .build());
    }

    protected void saveWorkshopLine(UUID branchId, UUID busId, UUID categoryId, String categoryName, String type,
                                    String amount, LocalDate serviceDate) {
        factWorkshopLineRepository.save(FactWorkshopLineEntity.builder()
                .lineId(UUID.randomUUID())
                .expenseId(UUID.randomUUID())
                .busId(busId)
                .branchId(branchId)
                .serviceDate(serviceDate)
                .categoryId(categoryId)
                .categoryName(categoryName)
                .type(type)
                .description("Trabajo de " + categoryName)
                .amount(new BigDecimal(amount))
                .build());
    }

    protected void saveDepreciation(UUID busId, UUID branchId, String km, String amount, Instant arrivedAt) {
        factDepreciationRepository.save(FactDepreciationEntity.builder()
                .tripId(UUID.randomUUID())
                .busId(busId)
                .branchId(branchId)
                .km(new BigDecimal(km))
                .rateApplied(new BigDecimal("0.7500"))
                .amount(new BigDecimal(amount))
                .arrivedAt(arrivedAt)
                .build());
    }

    protected void saveRate(String amountPerKm, Instant effectiveFrom) {
        dimDepreciationRateRepository.save(DimDepreciationRateEntity.builder()
                .rateId(UUID.randomUUID())
                .amountPerKm(new BigDecimal(amountPerKm))
                .effectiveFrom(effectiveFrom)
                .build());
    }

    protected int count(String sql, Object... args) {
        Integer total = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return total == null ? 0 : total;
    }

    private static EmbeddedPostgres startPostgres() {
        try {
            EmbeddedPostgres postgres = EmbeddedPostgres.builder().start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.close();
                } catch (IOException ignored) {
                }
            }));
            return postgres;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
