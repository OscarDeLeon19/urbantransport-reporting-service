package com.urbantransport.reporting.service;

import com.urbantransport.reporting.event.BranchEventPayload;
import com.urbantransport.reporting.event.BusEventPayload;
import com.urbantransport.reporting.event.DriverEventPayload;
import com.urbantransport.reporting.event.RouteEventPayload;

import java.util.UUID;

public interface DimensionService {

    void applyBranch(UUID eventId, String eventType, BranchEventPayload payload);

    void applyBus(UUID eventId, String eventType, BusEventPayload payload);

    void applyDriver(UUID eventId, String eventType, DriverEventPayload payload);

    void applyRoute(UUID eventId, String eventType, RouteEventPayload payload);
}
