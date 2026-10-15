package com.secondlap.backend.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String code,
        String message,
        Map<String, Object> context
) {
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null);
    }
}
