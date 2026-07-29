package com.orbisflow.notifications.api;

import com.orbisflow.auth.domain.JwtService.AuthenticatedUser;
import com.orbisflow.common.api.PageResponse;
import com.orbisflow.notifications.api.NotificationDtos.NotificationView;
import com.orbisflow.notifications.application.NotificationService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    ResponseEntity<PageResponse<NotificationView>> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String view,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(
                notifications.list(principal, view, page, size));
    }

    @PatchMapping("/{notificationId}/read")
    ResponseEntity<NotificationView> markRead(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID notificationId) {
        return ResponseEntity.ok(
                notifications.markRead(principal, notificationId));
    }
}
