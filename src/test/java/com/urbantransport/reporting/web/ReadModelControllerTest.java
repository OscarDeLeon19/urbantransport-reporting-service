package com.urbantransport.reporting.web;

import com.urbantransport.reporting.config.SecurityConfig;
import com.urbantransport.reporting.dto.RebuildResponse;
import com.urbantransport.reporting.exception.ApiException;
import com.urbantransport.reporting.exception.ErrorCodes;
import com.urbantransport.reporting.service.ReadModelRebuildService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReadModelController.class)
@Import(SecurityConfig.class)
class ReadModelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReadModelRebuildService readModelRebuildService;

    @Test
    void rebuild_asSystemAdmin_returnsAccepted() throws Exception {
        when(readModelRebuildService.rebuild()).thenReturn(new RebuildResponse("reporting-service", 14,
                List.of("dim_branch", "ledger_entries"), "Tablas vaciadas"));

        mockMvc.perform(post("/read-models/rebuild").with(user("SYSTEM_ADMIN", null)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.consumerGroup").value("reporting-service"))
                .andExpect(jsonPath("$.partitionsReset").value(14))
                .andExpect(jsonPath("$.clearedTables[1]").value("ledger_entries"));
    }

    @Test
    void rebuildInProgress_returnsConflict() throws Exception {
        when(readModelRebuildService.rebuild()).thenThrow(new ApiException(HttpStatus.CONFLICT,
                ErrorCodes.REBUILD_IN_PROGRESS, "Ya hay una reconstruccion en curso"));

        mockMvc.perform(post("/read-models/rebuild").with(user("SYSTEM_ADMIN", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCodes.REBUILD_IN_PROGRESS));
    }

    @Test
    void kafkaDown_returnsServiceUnavailable() throws Exception {
        when(readModelRebuildService.rebuild()).thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCodes.KAFKA_UNAVAILABLE, "Kafka no responde"));

        mockMvc.perform(post("/read-models/rebuild").with(user("SYSTEM_ADMIN", null)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCodes.KAFKA_UNAVAILABLE));
    }

    @Test
    void rebuild_asBranchAdmin_isDenied() throws Exception {
        mockMvc.perform(post("/read-models/rebuild").with(user("BRANCH_ADMIN",
                        UUID.fromString("11111111-1111-1111-1111-111111111111"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.ACCESS_DENIED));

        verifyNoInteractions(readModelRebuildService);
    }

    @Test
    void rebuild_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/read-models/rebuild"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(readModelRebuildService);
    }

    private static RequestPostProcessor user(String role, UUID branchId) {
        return jwt()
                .jwt(token -> {
                    token.subject("99999999-9999-9999-9999-999999999999").claim("roles", List.of(role));
                    if (branchId != null) {
                        token.claim("branchId", branchId.toString());
                    }
                })
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
