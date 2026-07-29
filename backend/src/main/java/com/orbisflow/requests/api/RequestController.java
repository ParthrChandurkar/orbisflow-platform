package com.orbisflow.requests.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.orbisflow.auth.domain.JwtService.AuthenticatedUser;
import com.orbisflow.requests.api.RequestDtos.CorrectionResult;
import com.orbisflow.requests.api.RequestDtos.ExpectedVersion;
import com.orbisflow.requests.api.RequestDtos.ExtractionView;
import com.orbisflow.requests.api.RequestDtos.RequestDetail;
import com.orbisflow.requests.api.RequestDtos.RequestSummary;
import com.orbisflow.requests.application.EmployeeExtractionService;
import com.orbisflow.requests.application.RequestCommandService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/requests")
public class RequestController {
    private final RequestCommandService commands;
    private final EmployeeExtractionService extractions;

    public RequestController(
            RequestCommandService commands,
            EmployeeExtractionService extractions) {
        this.commands = commands;
        this.extractions = extractions;
    }

    @PostMapping
    @PreAuthorize("hasRole('EMPLOYEE')")
    ResponseEntity<RequestSummary> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestAttribute("correlationId") String correlationId,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return ResponseEntity.accepted().body(commands.create(principal, file, correlationId));
    }

    @GetMapping("/{requestId}/extracted-data")
    ResponseEntity<ExtractionView> extractedData(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID requestId) {
        return ResponseEntity.ok(extractions.get(principal, requestId));
    }

    @PatchMapping("/{requestId}/extracted-data")
    @PreAuthorize("hasRole('EMPLOYEE')")
    ResponseEntity<CorrectionResult> correct(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID requestId,
            @RequestBody JsonNode body) {
        return ResponseEntity.ok(extractions.correct(principal, requestId, body));
    }

    @PostMapping("/{requestId}/resubmit")
    @PreAuthorize("hasRole('EMPLOYEE')")
    ResponseEntity<RequestDetail> resubmit(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID requestId,
            @RequestBody ExpectedVersion body) {
        return ResponseEntity.ok(
                extractions.resubmit(
                        principal,
                        requestId,
                        body == null ? null : body.expectedVersion()));
    }

    @PostMapping("/{requestId}/extraction/retry")
    @PreAuthorize("hasRole('EMPLOYEE')")
    ResponseEntity<RequestSummary> retry(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestAttribute("correlationId") String correlationId,
            @PathVariable UUID requestId,
            @RequestBody ExpectedVersion body) {
        return ResponseEntity.accepted().body(
                extractions.retry(
                        principal,
                        requestId,
                        body == null ? null : body.expectedVersion(),
                        correlationId));
    }
}
