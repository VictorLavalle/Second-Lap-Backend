package com.secondlap.backend.product.dto;

import com.secondlap.backend.product.ProductCondition;
import com.secondlap.backend.product.ProductStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductFilter(
        UUID category,
        String size,
        ProductCondition condition,
        ProductStatus status,
        BigDecimal maxPrice
) {
}
