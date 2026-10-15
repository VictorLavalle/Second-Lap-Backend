package com.secondlap.backend.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record AdminProductRequest(
        @NotBlank String name,
        @NotNull String description,
        @NotNull @DecimalMin("0.0") BigDecimal price,
        @NotNull UUID categoryId,
        @NotBlank String size,
        @NotBlank String condition,
        String brand,
        String color,
        String measurements
) {
}
