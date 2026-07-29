package com.orbisflow.dashboards.api;

import com.orbisflow.auth.domain.JwtService.AuthenticatedUser;
import com.orbisflow.common.api.PageResponse;
import com.orbisflow.dashboards.api.DashboardDtos.TeamActivity;
import com.orbisflow.dashboards.application.DashboardQueryService;
import com.orbisflow.requests.api.RequestDtos.RequestSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboards")
public class DashboardController {
    private final DashboardQueryService queries;

    public DashboardController(DashboardQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/manager/requests")
    @PreAuthorize("hasRole('MANAGER')")
    ResponseEntity<PageResponse<RequestSummary>> requests(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return ResponseEntity.ok(
                queries.managerRequests(
                        principal, status, page, size, sort, direction));
    }

    @GetMapping("/manager/team-activity")
    @PreAuthorize("hasRole('MANAGER')")
    ResponseEntity<TeamActivity> teamActivity(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(queries.teamActivity(principal));
    }

    @GetMapping("/finance/requests")
    @PreAuthorize("hasRole('FINANCE')")
    ResponseEntity<PageResponse<RequestSummary>> financeRequests(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return ResponseEntity.ok(
                queries.financeRequests(
                        principal, status, page, size, sort, direction));
    }
}
