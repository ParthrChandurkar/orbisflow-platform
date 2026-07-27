package com.orbisflow;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TestProtectedController {
    @PostMapping("/api/v1/test/csrf")
    ResponseEntity<Map<String, String>> csrfProtected() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
