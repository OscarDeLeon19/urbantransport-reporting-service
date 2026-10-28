package com.urbantransport.reporting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka.topic")
public record ReportingKafkaProperties(
        String identityEvents,
        String fleetEvents,
        String tripEvents,
        String ticketEvents,
        String rentalEvents,
        String financeEvents) {
}
