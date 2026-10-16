package com.secondlap.backend.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable pagination envelope for the API, decoupled from Spring Data's internal
 * Page/PageImpl serialization (which Spring Boot 3 warns about and may change
 * across versions). Shape: { content, totalElements, totalPages, number, size }.
 */
public record PageResponse<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int number,
        int size
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize());
    }
}
