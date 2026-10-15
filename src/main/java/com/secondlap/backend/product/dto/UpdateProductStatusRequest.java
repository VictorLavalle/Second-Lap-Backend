package com.secondlap.backend.product.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateProductStatusRequest(
        @NotBlank String status
) {
}
