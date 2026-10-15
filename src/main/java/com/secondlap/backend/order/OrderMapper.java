package com.secondlap.backend.order;

import com.secondlap.backend.order.dto.CustomerResponse;
import com.secondlap.backend.order.dto.OrderItemResponse;
import com.secondlap.backend.order.dto.OrderResponse;
import com.secondlap.backend.order.dto.ShippingAddressResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order, String storeWhatsapp) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderItemResponse(
                        item.getProduct().getPublicId().toString(),
                        item.getProduct().getName(),
                        item.getProduct().getSize(),
                        item.getPriceSnapshot()))
                .toList();

        return new OrderResponse(
                order.getPublicId().toString(),
                items,
                toCustomerResponse(order),
                order.getDeliveryMethod().getValue(),
                order.getPaymentMethod().getValue(),
                toAddressResponse(order),
                order.getSubtotal(),
                order.getShippingFee(),
                order.getTotal(),
                order.getStatus().getValue(),
                order.getCreatedAt(),
                storeWhatsapp);
    }

    private CustomerResponse toCustomerResponse(Order order) {
        return new CustomerResponse(
                order.getCustomer().getName(),
                order.getCustomer().getWhatsapp(),
                order.getCustomer().getComments());
    }

    private ShippingAddressResponse toAddressResponse(Order order) {
        Address address = order.getAddress();
        if (address == null) {
            return null;
        }
        return new ShippingAddressResponse(
                address.getStreet(),
                address.getBetweenStreets(),
                address.getNeighborhood(),
                address.getShippingFee(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getReferenceNotes(),
                address.getMapsLink());
    }
}
