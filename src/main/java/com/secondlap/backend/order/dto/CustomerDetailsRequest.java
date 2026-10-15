package com.secondlap.backend.order.dto;

import jakarta.validation.constraints.NotBlank;

public record CustomerDetailsRequest(
        @NotBlank String name,
        @NotBlank String whatsapp,
        String comments
) {
}
