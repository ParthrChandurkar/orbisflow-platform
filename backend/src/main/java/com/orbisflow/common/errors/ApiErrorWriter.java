package com.orbisflow.common.errors;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;

public final class ApiErrorWriter {
    private ApiErrorWriter() {
    }

    public static void write(
            ObjectMapper objectMapper,
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            ApiErrorCode code,
            String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), new ApiErrorEnvelope(
                new ApiErrorEnvelope.ErrorBody(code.name(), message, List.of()),
                (String) request.getAttribute("correlationId")));
    }
}
