package com.secondlap.backend.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record OrderEventResponse(
        String fromStatus,
        String toStatus,
        String actor,
        OffsetDateTime occurredAt
) {
}
