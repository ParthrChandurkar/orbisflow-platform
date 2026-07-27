package com.orbisflow.users.api;

import com.orbisflow.auth.domain.JwtService.AuthenticatedUser;
import com.orbisflow.users.api.UserDtos.UserView;
import com.orbisflow.users.application.UserQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserQueryService userQueryService;

    public UserController(UserQueryService userQueryService) {
        this.userQueryService = userQueryService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserView> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(UserView.from(userQueryService.currentUser(principal.id())));
    }
}
