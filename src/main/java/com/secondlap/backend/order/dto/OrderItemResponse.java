package com.secondlap.backend.order.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        String productId,
        String name,
        String size,
        BigDecimal price
) {
}
