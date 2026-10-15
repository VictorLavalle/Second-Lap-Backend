package com.secondlap.backend.order.dto;

public record CustomerResponse(
        String name,
        String whatsapp,
        String comments
) {
}
