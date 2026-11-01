package com.urbantransport.reporting.service.impl;

import com.urbantransport.reporting.domain.LedgerEntryEntity;
import com.urbantransport.reporting.repository.LedgerEntryRepository;
import com.urbantransport.reporting.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerServiceImpl implements LedgerService {

    private static final int MAX_DESCRIPTION = 255;

    private final LedgerEntryRepository ledgerEntryRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(LedgerEntryEntity entry) {
        if (entry.getAmount() == null || entry.getAmount().signum() == 0) {
            log.debug("Asiento {} {} sin monto, no se registra", entry.getSourceType(), entry.getSourceId());
            return;
        }
        if (ledgerEntryRepository.existsBySourceTypeAndSourceId(entry.getSourceType(), entry.getSourceId())) {
            log.debug("Asiento {} {} ya registrado, se ignora", entry.getSourceType(), entry.getSourceId());
            return;
        }
        if (entry.getBranchId() == null) {
            log.warn("Asiento {} {} sin sucursal; se completará cuando llegue la dimensión",
                    entry.getSourceType(), entry.getSourceId());
        }

        entry.setAmount(entry.getAmount().setScale(2, RoundingMode.HALF_UP));
        if (entry.getDescription() != null && entry.getDescription().length() > MAX_DESCRIPTION) {
            entry.setDescription(entry.getDescription().substring(0, MAX_DESCRIPTION));
        }
        ledgerEntryRepository.save(entry);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void assignBusBranch(UUID busId, UUID branchId) {
        int updated = ledgerEntryRepository.assignBranchToBusTrips(busId, branchId);
        if (updated > 0) {
            log.info("{} asientos del bus {} quedaron en la sucursal {}", updated, busId, branchId);
        }
    }
}
