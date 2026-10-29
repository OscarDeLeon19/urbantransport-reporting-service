package com.urbantransport.reporting.dto;

import lombok.Value;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Value
public class ProfitReportResponse {
    List<BranchProfit> branches;
    BigDecimal totalIncome;
    BigDecimal totalExpense;
    BigDecimal totalNet;

    @Value
    public static class BranchProfit {
        UUID branchId;
        String branchName;
        BigDecimal income;
        BigDecimal expense;
        BigDecimal net;
    }
}
