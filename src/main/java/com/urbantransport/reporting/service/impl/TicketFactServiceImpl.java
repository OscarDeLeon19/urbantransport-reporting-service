package com.urbantransport.reporting.service.impl;

import com.urbantransport.reporting.domain.FactTicketSaleEntity;
import com.urbantransport.reporting.domain.FactTripEntity;
import com.urbantransport.reporting.domain.LedgerConcept;
import com.urbantransport.reporting.domain.LedgerEntryEntity;
import com.urbantransport.reporting.domain.LedgerEntryType;
import com.urbantransport.reporting.domain.LedgerSourceType;
import com.urbantransport.reporting.domain.ProcessedEventEntity;
import com.urbantransport.reporting.event.TicketCancelledPayload;
import com.urbantransport.reporting.event.TicketPurchasedPayload;
import com.urbantransport.reporting.repository.FactTicketSaleRepository;
import com.urbantransport.reporting.repository.FactTripRepository;
import com.urbantransport.reporting.repository.ProcessedEventRepository;
import com.urbantransport.reporting.service.LedgerService;
import com.urbantransport.reporting.service.TicketFactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketFactServiceImpl implements TicketFactService {

    private static final String ONLINE = "ONLINE";
    private static final String REFUND_WALLET = "WALLET";

    private final FactTicketSaleRepository factTicketSaleRepository;
    private final FactTripRepository factTripRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final LedgerService ledgerService;

    @Value("${app.zone:America/Guatemala}")
    private ZoneId zone;

    @Override
    @Transactional
    public void applyTicketPurchased(UUID eventId, String eventType, Instant occurredAt,
                                     TicketPurchasedPayload payload) {
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Evento {} ya procesado, se ignora", eventId);
            return;
        }

        UUID tripId = payload.trip() != null ? payload.trip().tripId() : null;
        FactTripEntity trip = tripId == null ? null : factTripRepository.findById(tripId).orElse(null);
        UUID branchId = payload.branchId() != null ? payload.branchId()
                : trip != null ? trip.getOriginBranchId() : null;
        List<TicketPurchasedPayload.TicketInfo> tickets = payload.tickets() == null ? List.of() : payload.tickets();

        List<FactTicketSaleEntity> sales = tickets.stream()
                .map(ticket -> {
                    FactTicketSaleEntity sale = factTicketSaleRepository.findById(ticket.ticketId())
                            .orElseGet(() -> FactTicketSaleEntity.builder().ticketId(ticket.ticketId()).build());
                    sale.setOrderId(payload.orderId());
                    sale.setTripId(tripId);
                    sale.setRouteId(trip != null ? trip.getRouteId() : null);
                    sale.setBusId(trip != null ? trip.getBusId() : null);
                    sale.setBranchId(branchId);
                    sale.setChannel(payload.channel());
                    sale.setSeatNumber(ticket.seatNumber());
                    sale.setPricePaid(ticket.pricePaid());
                    sale.setPaymentDate(payload.paymentDate());
                    return sale;
                })
                .toList();
        factTicketSaleRepository.saveAll(sales);

        BigDecimal amount = tickets.stream()
                .map(TicketPurchasedPayload.TicketInfo::pricePaid)
                .filter(price -> price != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        ledgerService.record(LedgerEntryEntity.builder()
                .type(LedgerEntryType.INCOME)
                .concept(LedgerConcept.TICKET_SALE)
                .branchId(branchId)
                .amount(amount)
                .occurredOn(payload.paymentDate() != null ? payload.paymentDate() : dateOf(occurredAt))
                .sourceType(LedgerSourceType.TICKET_SALE)
                .sourceId(payload.orderId())
                .description(purchaseDescription(payload, tickets.size()))
                .build());

        markProcessed(eventId, eventType);
        log.info("Orden {} sincronizada: {} boletos por {}", payload.orderId(), tickets.size(), amount);
    }

    @Override
    @Transactional
    public void applyTicketCancelled(UUID eventId, String eventType, Instant occurredAt,
                                     TicketCancelledPayload payload) {
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Evento {} ya procesado, se ignora", eventId);
            return;
        }

        FactTicketSaleEntity sale = factTicketSaleRepository.findById(payload.ticketId())
                .orElseGet(() -> FactTicketSaleEntity.builder()
                        .ticketId(payload.ticketId())
                        .orderId(payload.orderId())
                        .tripId(payload.tripId())
                        .branchId(payload.branchId())
                        .channel(payload.channel())
                        .seatNumber(payload.seatNumber())
                        .pricePaid(payload.pricePaid())
                        .build());
        Instant cancelledAt = occurredAt != null ? occurredAt : Instant.now();
        sale.setCancelled(true);
        sale.setCancelledAt(cancelledAt);
        factTicketSaleRepository.save(sale);

        BigDecimal pricePaid = payload.pricePaid() != null ? payload.pricePaid() : sale.getPricePaid();
        ledgerService.record(LedgerEntryEntity.builder()
                .type(LedgerEntryType.INCOME)
                .concept(LedgerConcept.TICKET_SALE)
                .branchId(payload.branchId() != null ? payload.branchId() : sale.getBranchId())
                .amount(pricePaid == null ? null : pricePaid.negate())
                .occurredOn(dateOf(cancelledAt))
                .sourceType(LedgerSourceType.TICKET_CANCELLATION)
                .sourceId(payload.ticketId())
                .description(cancellationDescription(payload))
                .build());

        markProcessed(eventId, eventType);
        log.info("Boleto {} cancelado", payload.ticketId());
    }

    private String purchaseDescription(TicketPurchasedPayload payload, int tickets) {
        String description = ONLINE.equals(payload.channel()) ? "Venta en línea" : "Venta en mostrador";
        if (payload.trip() != null && payload.trip().originName() != null) {
            description += ": " + payload.trip().originName() + " - " + payload.trip().destinationName();
        }
        return description + " (" + tickets + (tickets == 1 ? " boleto)" : " boletos)");
    }

    private String cancellationDescription(TicketCancelledPayload payload) {
        String seat = payload.seatLabel() != null ? payload.seatLabel() : String.valueOf(payload.seatNumber());
        String refund = REFUND_WALLET.equals(payload.refundMethod()) ? "reembolso a cartera" : "reembolso en efectivo";
        return "Cancelación del boleto, asiento " + seat + " (" + refund + ")";
    }

    private LocalDate dateOf(Instant instant) {
        return instant != null ? LocalDate.ofInstant(instant, zone) : LocalDate.now(zone);
    }

    private void markProcessed(UUID eventId, String eventType) {
        processedEventRepository.save(ProcessedEventEntity.builder()
                .eventId(eventId)
                .eventType(eventType)
                .build());
    }
}
