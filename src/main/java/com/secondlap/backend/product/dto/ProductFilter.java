package com.secondlap.backend.product.dto;

import com.secondlap.backend.product.ProductCondition;
import com.secondlap.backend.product.ProductStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductFilter(
        UUID category,
        List<String> sizes,
        List<ProductCondition> conditions,
        ProductStatus status,
        BigDecimal maxPrice
) {
}
