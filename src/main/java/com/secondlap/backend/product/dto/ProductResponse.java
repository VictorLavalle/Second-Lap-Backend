package com.secondlap.backend.product.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProductResponse(
        String id,
        String name,
        String description,
        BigDecimal price,
        List<String> images,
        String categoryId,
        String size,
        String condition,
        String brand,
        String color,
        String measurements,
        String status
) {
}
