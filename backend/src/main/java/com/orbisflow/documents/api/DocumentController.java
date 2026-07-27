package com.orbisflow.documents.api;

import com.orbisflow.auth.domain.JwtService.AuthenticatedUser;
import com.orbisflow.documents.api.DocumentDtos.AccessLink;
import com.orbisflow.documents.application.DocumentService;
import com.orbisflow.requests.api.RequestDtos.RequestSummary;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class DocumentController {
    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @PostMapping("/requests/{requestId}/documents")
    @PreAuthorize("hasRole('EMPLOYEE')")
    ResponseEntity<RequestSummary> replace(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID requestId,
            @RequestParam("expected_version") long expectedVersion,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return ResponseEntity.accepted().body(
                documents.replace(principal, requestId, expectedVersion, file));
    }

    @GetMapping("/documents/{documentId}/access-link")
    ResponseEntity<AccessLink> accessLink(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID documentId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(documents.createAccessLink(principal, documentId));
    }

    @GetMapping("/documents/{documentId}/content")
    ResponseEntity<byte[]> content(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID documentId,
            @RequestParam(required = false) String token) {
        var content = documents.content(principal, documentId, token);
        String disposition = ContentDisposition.attachment()
                .filename(
                        content.document().originalFilename(), StandardCharsets.UTF_8)
                .build().toString();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(content.document().mimeType()))
                .contentLength(content.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(content.bytes());
    }
}
