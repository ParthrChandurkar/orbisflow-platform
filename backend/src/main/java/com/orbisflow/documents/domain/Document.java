package com.orbisflow.documents.domain;

import java.time.Instant;
import java.util.UUID;

public record Document(
        UUID id,
        UUID requestId,
        UUID uploadedByUserId,
        String s3ObjectKey,
        String originalFilename,
        String mimeType,
        long fileSizeBytes,
        boolean current,
        Instant createdAt
) {
}
