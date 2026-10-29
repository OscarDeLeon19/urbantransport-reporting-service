package com.urbantransport.reporting.exception;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ErrorCodes {

    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String BRANCH_REQUIRED = "BRANCH_REQUIRED";
    public static final String BRANCH_FORBIDDEN = "BRANCH_FORBIDDEN";
    public static final String INVALID_DATE_RANGE = "INVALID_DATE_RANGE";
    public static final String INVALID_FORMAT = "INVALID_FORMAT";
    public static final String BRANCH_NOT_FOUND = "BRANCH_NOT_FOUND";
    public static final String PDF_GENERATION_FAILED = "PDF_GENERATION_FAILED";
    public static final String REBUILD_IN_PROGRESS = "REBUILD_IN_PROGRESS";
    public static final String KAFKA_UNAVAILABLE = "KAFKA_UNAVAILABLE";
}
