package com.secondlap.backend.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String REQUEST_ID = "requestId";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        log.debug("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ProductNotAvailableException.class)
    public ResponseEntity<ApiError> handleNotAvailable(ProductNotAvailableException ex) {
        log.warn("Product not available: {}", ex.getProductId());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("PRODUCT_NOT_AVAILABLE", ex.getMessage(),
                        Map.of("productId", ex.getProductId())));
    }

    @ExceptionHandler(InvalidOrderTransitionException.class)
    public ResponseEntity<ApiError> handleInvalidOrderTransition(InvalidOrderTransitionException ex) {
        log.warn("Invalid order transition: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("INVALID_ORDER_TRANSITION", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));
        log.debug("Validation failed: {}", fieldErrors);
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_ERROR", "Request validation failed", fieldErrors));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        log.debug("Bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiError.of("BAD_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(com.secondlap.backend.storage.InvalidUploadException.class)
    public ResponseEntity<ApiError> handleInvalidUpload(
            com.secondlap.backend.storage.InvalidUploadException ex) {
        log.debug("Invalid upload: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiError.of("INVALID_UPLOAD", ex.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> handleRateLimit(RateLimitExceededException ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "60")
                .body(ApiError.of("RATE_LIMITED", ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        // Only the parameter name is exposed: the raw value could reflect
        // unsanitized client input back in the response.
        log.debug("Invalid parameter: {}", ex.getName());
        return ResponseEntity.badRequest()
                .body(ApiError.of("INVALID_PARAMETER", "Parámetro inválido: " + ex.getName()));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handlePayloadTooLarge(
            org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        log.debug("Payload too large");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiError.of("PAYLOAD_TOO_LARGE", "El archivo o la solicitud excede el tamaño permitido"));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(
            org.springframework.http.converter.HttpMessageNotReadableException ex) {
        // Generic message only: the Jackson detail may contain Java types or the
        // raw input value, which must not be reflected back to the client.
        log.debug("Malformed request body");
        return ResponseEntity.badRequest()
                .body(ApiError.of("MALFORMED_REQUEST", "La solicitud no es válida o está mal formada."));
    }

    // Catch-all: log the full stack trace server-side under a correlation id and
    // hand that id back to the client so the 500 they saw can be traced in the
    // logs — without ever leaking the stack trace itself.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        String correlationId = currentCorrelationId();
        log.error("Unexpected error [correlationId={}]", correlationId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "Ocurrió un error inesperado",
                        Map.of("correlationId", correlationId)));
    }

    private String currentCorrelationId() {
        String requestId = MDC.get(REQUEST_ID);
        return requestId != null ? requestId : UUID.randomUUID().toString();
    }
}
