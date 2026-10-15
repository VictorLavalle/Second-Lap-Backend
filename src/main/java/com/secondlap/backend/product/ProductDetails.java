package com.secondlap.backend.product;

import com.secondlap.backend.category.Category;

import java.math.BigDecimal;

/**
 * Groups the mutable attributes of a product so creation and update share a
 * single cohesive parameter object instead of long positional argument lists.
 */
public record ProductDetails(
        String name,
        String description,
        BigDecimal price,
        Category category,
        String size,
        ProductCondition condition,
        String brand,
        String color,
        String measurements
) {
}
