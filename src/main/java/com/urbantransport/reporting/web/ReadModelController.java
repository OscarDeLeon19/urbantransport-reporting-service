package com.urbantransport.reporting.web;

import com.urbantransport.reporting.dto.RebuildResponse;
import com.urbantransport.reporting.service.ReadModelRebuildService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/read-models")
@RequiredArgsConstructor
public class ReadModelController {

    private final ReadModelRebuildService readModelRebuildService;

    @PostMapping("/rebuild")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public RebuildResponse rebuild() {
        return readModelRebuildService.rebuild();
    }
}
