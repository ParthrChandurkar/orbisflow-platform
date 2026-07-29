package com.orbisflow.documents.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public final class DocumentDtos {
    private DocumentDtos() {
    }

    public record AccessLink(
            String url,
            @JsonProperty("expires_at") Instant expiresAt
    ) {
    }
}
