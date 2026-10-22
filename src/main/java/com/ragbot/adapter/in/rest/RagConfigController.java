package com.ragbot.adapter.in.rest;

import com.ragbot.application.service.AuditLogService;
import com.ragbot.application.service.RagConfigService;
import com.ragbot.domain.entity.AuditAction;
import com.ragbot.domain.entity.RagConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller para configuração dinâmica do RAG (#25).
 *
 * GET  /api/admin/config  → retorna a configuração atual
 * PUT  /api/admin/config  → salva nova configuração (ADMIN only)
 *
 * As mudanças têm efeito imediato: o RagConfigService invalida o cache
 * e a próxima pergunta do chat já usará os novos parâmetros.
 */
@RestController
@RequestMapping("/api/admin/config")
@PreAuthorize("hasRole('ADMIN')")
public class RagConfigController {

    private final RagConfigService ragConfigService;
    private final AuditLogService  auditLogService;

    public RagConfigController(RagConfigService ragConfigService,
                               AuditLogService auditLogService) {
        this.ragConfigService = ragConfigService;
        this.auditLogService  = auditLogService;
    }

    @GetMapping
    public ResponseEntity<RagConfigResponse> getConfig() {
        return ResponseEntity.ok(RagConfigResponse.from(ragConfigService.getConfig()));
    }

    @PutMapping
    public ResponseEntity<RagConfigResponse> updateConfig(
            @Valid @RequestBody RagConfigRequest request,
            Authentication auth,
            HttpServletRequest httpRequest) {

        RagConfig updated = new RagConfig(
                request.topK(),
                request.minScore(),
                request.shortChunkLength(),
                request.hybridEnabled(),
                request.semanticWeight(),
                request.monthlyTokenLimit(),
                request.maxHistoryMessages()
        );

        ragConfigService.updateConfig(updated);

        auditLogService.record(auth.getName(), AuditAction.CONFIG_UPDATE, null, "RAG_CONFIG",
                "topK=" + updated.topK() + " minScore=" + updated.minScore()
                + " hybrid=" + updated.hybridEnabled(),
                httpRequest.getRemoteAddr());

        return ResponseEntity.ok(RagConfigResponse.from(updated));
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    record RagConfigResponse(
            int topK, double minScore, int shortChunkLength,
            boolean hybridEnabled, double semanticWeight, long monthlyTokenLimit,
            int maxHistoryMessages
    ) {
        static RagConfigResponse from(RagConfig c) {
            return new RagConfigResponse(c.topK(), c.minScore(), c.shortChunkLength(),
                    c.hybridEnabled(), c.semanticWeight(), c.monthlyTokenLimit(),
                    c.maxHistoryMessages());
        }
    }

    record RagConfigRequest(
            @Min(1) @Max(20)                                    int topK,
            @DecimalMin("0.0") @DecimalMax("1.0")               double minScore,
            @Min(10) @Max(500)                                  int shortChunkLength,
                                                                boolean hybridEnabled,
            @DecimalMin("0.0") @DecimalMax("1.0")               double semanticWeight,
            @Positive                                            long monthlyTokenLimit,
            @Min(1) @Max(50)                                    int maxHistoryMessages
    ) {}
}
