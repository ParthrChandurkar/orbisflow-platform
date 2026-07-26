package com.orbisflow.common.errors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorEnvelope> apiException(ApiException exception, HttpServletRequest request) {
        return response(exception.status(), exception.code(), exception.getMessage(), List.of(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorEnvelope> validation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<ApiErrorEnvelope.FieldError> fields = exception.getBindingResult()
                .getFieldErrors().stream().map(this::fieldError).toList();
        return response(
                HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_REQUEST,
                "The request contains invalid fields.", fields, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, ConstraintViolationException.class})
    ResponseEntity<ApiErrorEnvelope> malformed(Exception exception, HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_REQUEST,
                "The request is malformed.", List.of(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiErrorEnvelope> notFound(
            NoResourceFoundException exception, HttpServletRequest request) {
        return response(
                HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND,
                "The requested resource was not found.", List.of(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiErrorEnvelope> methodNotAllowed(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return response(
                HttpStatus.METHOD_NOT_ALLOWED, ApiErrorCode.METHOD_NOT_ALLOWED,
                "The HTTP method is not supported for this resource.", List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorEnvelope> unexpected(Exception exception, HttpServletRequest request) {
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred.", List.of(), request);
    }

    private ApiErrorEnvelope.FieldError fieldError(FieldError error) {
        return new ApiErrorEnvelope.FieldError(
                error.getField(), "INVALID_VALUE", error.getDefaultMessage());
    }

    private ResponseEntity<ApiErrorEnvelope> response(
            HttpStatus status,
            ApiErrorCode code,
            String message,
            List<ApiErrorEnvelope.FieldError> fields,
            HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiErrorEnvelope(
                new ApiErrorEnvelope.ErrorBody(code.name(), message, fields),
                (String) request.getAttribute("correlationId")));
    }
}
