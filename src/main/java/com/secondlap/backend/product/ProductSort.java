package com.secondlap.backend.product;

import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Single source of truth for which product fields can be sorted on, shared by
 * the public listing and the admin listing so they never diverge. An unknown or
 * blank sort falls back to the default (id,desc) instead of propagating a 500.
 */
public final class ProductSort {

    private static final Set<String> SORTABLE = Set.of("id", "price", "createdAt", "modifiedDate");
    private static final Sort DEFAULT = Sort.by(Sort.Direction.DESC, "id");

    private ProductSort() {
    }

    public static Sort parse(String sort) {
        if (sort == null || sort.isBlank()) {
            return DEFAULT;
        }
        String[] parts = sort.split(",", 2);
        String property = parts[0].trim();
        if (!SORTABLE.contains(property)) {
            return DEFAULT;
        }
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}
