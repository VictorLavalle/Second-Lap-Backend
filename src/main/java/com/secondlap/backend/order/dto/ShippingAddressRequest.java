package com.secondlap.backend.order.dto;

import jakarta.validation.constraints.NotBlank;

public record ShippingAddressRequest(
        @NotBlank String street,
        String betweenStreets,
        @NotBlank String neighborhood,
        @NotBlank String city,
        String state,
        @NotBlank String postalCode,
        String references,
        String mapsLink
) {
}
