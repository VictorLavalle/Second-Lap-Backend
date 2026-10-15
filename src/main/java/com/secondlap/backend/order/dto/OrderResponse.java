package com.secondlap.backend.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record OrderResponse(
        String id,
        List<OrderItemResponse> items,
        CustomerResponse customer,
        String deliveryMethod,
        String paymentMethod,
        ShippingAddressResponse address,
        BigDecimal subtotal,
        BigDecimal shippingFee,
        BigDecimal total,
        String status,
        OffsetDateTime createdAt,
        String storeWhatsapp
) {
}
