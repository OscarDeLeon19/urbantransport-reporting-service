package com.urbantransport.reporting.dto;

import lombok.Value;

import java.util.List;

@Value
public class RebuildResponse {
    String consumerGroup;
    int partitionsReset;
    List<String> clearedTables;
    String message;
}
