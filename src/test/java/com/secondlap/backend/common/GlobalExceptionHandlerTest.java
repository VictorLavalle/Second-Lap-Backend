package com.secondlap.backend.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsInvalidOrderTransitionTo409() {
        ResponseEntity<ApiError> response =
                handler.handleInvalidOrderTransition(
                        new InvalidOrderTransitionException("No se puede cancelar un pedido en estado confirmado"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("INVALID_ORDER_TRANSITION");
        assertThat(response.getBody().message()).contains("confirmado");
    }

    @Test
    void mapsTypeMismatchTo400InvalidParameter() {
        var ex = new org.springframework.web.method.annotation.MethodArgumentTypeMismatchException(
                "not-a-uuid", java.util.UUID.class, "id", null, new IllegalArgumentException("bad uuid"));

        ResponseEntity<ApiError> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_PARAMETER");
        assertThat(response.getBody().message()).contains("id");
        // raw value must not be reflected back
        assertThat(response.getBody().message()).doesNotContain("not-a-uuid");
    }

    @Test
    void mapsRateLimitTo429WithRetryAfter() {
        ResponseEntity<ApiError> response =
                handler.handleRateLimit(new RateLimitExceededException("slow down"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("60");
        assertThat(response.getBody().code()).isEqualTo("RATE_LIMITED");
    }

    @Test
    void mapsMaxUploadSizeTo413() {
        ResponseEntity<ApiError> response =
                handler.handlePayloadTooLarge(new MaxUploadSizeExceededException(5_000_000));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().code()).isEqualTo("PAYLOAD_TOO_LARGE");
    }

    @Test
    void mapsUnexpectedTo500WithoutLeakingDetails() {
        ResponseEntity<ApiError> response =
                handler.handleUnexpected(new RuntimeException("stack trace with secrets"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).doesNotContain("secrets");
    }

    @Test
    void unexpectedErrorCarriesCorrelationIdInContext() {
        ResponseEntity<ApiError> response =
                handler.handleUnexpected(new RuntimeException("boom"));

        assertThat(response.getBody().context()).containsKey("correlationId");
        assertThat(response.getBody().context().get("correlationId")).asString().isNotEmpty();
    }

    @Test
    void unexpectedErrorReusesRequestIdFromMdcAsCorrelationId() {
        org.slf4j.MDC.put("requestId", "fixed-request-id");
        try {
            ResponseEntity<ApiError> response =
                    handler.handleUnexpected(new RuntimeException("boom"));
            assertThat(response.getBody().context().get("correlationId")).isEqualTo("fixed-request-id");
        } finally {
            org.slf4j.MDC.clear();
        }
    }

    @Test
    void clientErrorsHaveNoContext() {
        ResponseEntity<ApiError> notFound =
                handler.handleNotFound(new ResourceNotFoundException("missing"));
        assertThat(notFound.getBody().context()).isNull();

        ResponseEntity<ApiError> transition =
                handler.handleInvalidOrderTransition(new InvalidOrderTransitionException("nope"));
        assertThat(transition.getBody().context()).isNull();
    }
}
