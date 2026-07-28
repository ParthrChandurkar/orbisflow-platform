package com.orbisflow.audit.api;

import com.orbisflow.audit.api.AuditDtos.AuditEventView;
import com.orbisflow.audit.application.AuditService;
import com.orbisflow.auth.domain.JwtService.AuthenticatedUser;
import com.orbisflow.common.api.PageResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/requests")
public class AuditController {
    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping("/{requestId}/audit")
    ResponseEntity<PageResponse<AuditEventView>> get(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID requestId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(audit.get(principal, requestId, page, size));
    }
}
