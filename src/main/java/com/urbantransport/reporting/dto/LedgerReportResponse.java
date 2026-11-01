package com.urbantransport.reporting.dto;

import com.urbantransport.reporting.domain.LedgerConcept;
import com.urbantransport.reporting.domain.LedgerEntryEntity;
import com.urbantransport.reporting.domain.LedgerEntryType;
import com.urbantransport.reporting.domain.LedgerSourceType;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Value
public class LedgerReportResponse {
    LedgerEntryType type;
    List<BranchGroup> branches;
    BigDecimal total;

    @Value
    public static class BranchGroup {
        UUID branchId;
        String branchName;
        List<Entry> entries;
        BigDecimal subtotal;
    }

    @Value
    public static class Entry {
        UUID id;
        LocalDate date;
        LedgerConcept concept;
        LedgerSourceType sourceType;
        UUID sourceId;
        String description;
        BigDecimal amount;

        public static Entry fromEntity(LedgerEntryEntity entry) {
            return new Entry(
                    entry.getId(),
                    entry.getOccurredOn(),
                    entry.getConcept(),
                    entry.getSourceType(),
                    entry.getSourceId(),
                    entry.getDescription(),
                    entry.getAmount());
        }
    }
}
