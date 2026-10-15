package com.secondlap.backend.order.dto;

import java.math.BigDecimal;

public record ShippingAddressResponse(
        String street,
        String betweenStreets,
        String neighborhood,
        BigDecimal shippingFee,
        String city,
        String state,
        String postalCode,
        String references,
        String mapsLink
) {
}
