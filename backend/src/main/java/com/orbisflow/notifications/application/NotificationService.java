package com.orbisflow.notifications.application;

import com.orbisflow.auth.domain.JwtService.AuthenticatedUser;
import com.orbisflow.common.api.PageResponse;
import com.orbisflow.common.errors.ApiErrorCode;
import com.orbisflow.common.errors.ApiException;
import com.orbisflow.notifications.api.NotificationDtos.NotificationView;
import com.orbisflow.notifications.persistence.NotificationRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private static final int RECENT_LIMIT = 50;
    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    public PageResponse<NotificationView> list(
            AuthenticatedUser principal,
            String view,
            Integer page,
            Integer size) {
        String effectiveView = view == null ? "recent" : view;
        int effectivePage = page == null ? 0 : page;
        int effectiveSize = size == null ? 20 : size;
        boolean unread = effectiveView.equals("unread");
        if ((!effectiveView.equals("recent") && !unread)
                || effectivePage < 0
                || effectiveSize < 1
                || effectiveSize > 50) {
            throw invalid();
        }
        var items = repository.findOwned(
                        principal.id(),
                        unread,
                        effectivePage,
                        effectiveSize,
                        RECENT_LIMIT)
                .stream()
                .map(NotificationView::from)
                .toList();
        long count = repository.countOwned(
                principal.id(), unread, RECENT_LIMIT);
        return PageResponse.of(
                items,
                effectivePage,
                effectiveSize,
                count,
                "created_at",
                "desc");
    }

    public NotificationView markRead(
            AuthenticatedUser principal,
            UUID notificationId) {
        repository.findOwnedById(principal.id(), notificationId)
                .orElseThrow(NotificationService::notFound);
        repository.markRead(principal.id(), notificationId);
        return NotificationView.from(
                repository.findOwnedById(principal.id(), notificationId)
                        .orElseThrow(NotificationService::notFound));
    }

    private ApiException invalid() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.INVALID_REQUEST,
                "The notification query parameters are invalid.");
    }

    private static ApiException notFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.RESOURCE_NOT_FOUND,
                "The requested resource was not found.");
    }
}
