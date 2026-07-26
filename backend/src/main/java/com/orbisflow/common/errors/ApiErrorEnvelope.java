package com.orbisflow.common.errors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ApiErrorEnvelope(
        ErrorBody error,
        @JsonProperty("correlation_id") String correlationId
) {
    public record ErrorBody(
            String code,
            String message,
            @JsonProperty("field_errors")
            @JsonInclude(JsonInclude.Include.NON_EMPTY)
            List<FieldError> fieldErrors
    ) {
    }

    public record FieldError(String field, String code, String message) {
    }
}
