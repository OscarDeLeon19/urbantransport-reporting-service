package com.urbantransport.reporting.dto;

import com.urbantransport.reporting.exception.ApiException;
import com.urbantransport.reporting.exception.ErrorCodes;
import org.springframework.http.HttpStatus;

public enum ReportFormat {
    JSON,
    PDF;

    public static ReportFormat from(String value) {
        for (ReportFormat format : values()) {
            if (format.name().equalsIgnoreCase(value.trim())) {
                return format;
            }
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_FORMAT,
                "Formato inválido: usa json o pdf");
    }
}
