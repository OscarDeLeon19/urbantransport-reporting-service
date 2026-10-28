package com.urbantransport.reporting.event;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EventTypes {

    public static final String BRANCH_CREATED = "BranchCreated";
    public static final String BRANCH_UPDATED = "BranchUpdated";

    public static final String BUS_REGISTERED = "BusRegistered";
    public static final String BUS_UPDATED = "BusUpdated";
    public static final String BUS_DEACTIVATED = "BusDeactivated";
    public static final String DRIVER_REGISTERED = "DriverRegistered";
    public static final String DRIVER_UPDATED = "DriverUpdated";
    public static final String DRIVER_DEACTIVATED = "DriverDeactivated";
    public static final String ROUTE_CREATED = "RouteCreated";
    public static final String ROUTE_DELETED = "RouteDeleted";

    public static final String TRIP_SCHEDULED = "TripScheduled";
    public static final String TRIP_DELETED = "TripDeleted";
    public static final String TRIP_STARTED = "TripStarted";
    public static final String TRIP_COMPLETED = "TripCompleted";

    public static final String TICKET_PURCHASED = "TicketPurchased";
    public static final String TICKET_CANCELLED = "TicketCancelled";

    public static final String RENTAL_PAID = "RentalPaid";

    public static final String WORKSHOP_EXPENSE_REGISTERED = "WorkshopExpenseRegistered";
    public static final String DEPRECIATION_RATE_CHANGED = "DepreciationRateChanged";
    public static final String DEPRECIATION_CALCULATED = "DepreciationCalculated";
}
