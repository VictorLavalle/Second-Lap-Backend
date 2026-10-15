package com.secondlap.backend.neighborhood.dto;

import java.math.BigDecimal;

public record NeighborhoodResponse(
        String name,
        BigDecimal fee
) {
}
