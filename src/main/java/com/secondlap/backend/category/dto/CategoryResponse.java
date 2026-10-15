package com.secondlap.backend.category.dto;

public record CategoryResponse(
        String id,
        String name,
        int order
) {
}
