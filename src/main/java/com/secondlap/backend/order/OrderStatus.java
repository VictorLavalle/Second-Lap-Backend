package com.secondlap.backend.order;

public enum OrderStatus {
    PENDING("pendiente"),
    CONFIRMED("confirmado"),
    COMPLETED("completado"),
    DELIVERED("entregado"),
    CANCELLED("cancelado");

    private final String value;

    OrderStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static OrderStatus fromValue(String value) {
        for (OrderStatus s : values()) {
            if (s.value.equals(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown order status: " + value);
    }
}
