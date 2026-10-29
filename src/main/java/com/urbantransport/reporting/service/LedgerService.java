package com.urbantransport.reporting.service;

import com.urbantransport.reporting.domain.LedgerEntryEntity;

import java.util.UUID;

public interface LedgerService {

    void record(LedgerEntryEntity entry);

    void assignBusBranch(UUID busId, UUID branchId);
}
