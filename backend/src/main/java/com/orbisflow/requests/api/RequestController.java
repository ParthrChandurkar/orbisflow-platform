package com.orbisflow.requests.api;

import com.orbisflow.auth.domain.JwtService.AuthenticatedUser;
import com.orbisflow.requests.api.RequestDtos.RequestSummary;
import com.orbisflow.requests.application.RequestCommandService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/requests")
public class RequestController {
    private final RequestCommandService commands;

    public RequestController(RequestCommandService commands) {
        this.commands = commands;
    }

    @PostMapping
    @PreAuthorize("hasRole('EMPLOYEE')")
    ResponseEntity<RequestSummary> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return ResponseEntity.accepted().body(commands.create(principal, file));
    }
}
