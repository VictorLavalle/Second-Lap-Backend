package com.secondlap.backend.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotEmpty List<UUID> productIds,
        @NotNull @Valid CustomerDetailsRequest customer,
        @NotBlank String deliveryMethod,
        @NotBlank String paymentMethod,
        @Valid ShippingAddressRequest address
) {
}
