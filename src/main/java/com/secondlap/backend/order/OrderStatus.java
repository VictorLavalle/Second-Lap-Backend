package com.secondlap.backend.order;

import java.util.Map;
import java.util.Set;

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

    // Single source of truth for the order workflow: a linear forward flow plus
    // cancellation allowed from any non-terminal state. DELIVERED and CANCELLED
    // are terminal.
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            PENDING, Set.of(CONFIRMED, CANCELLED),
            CONFIRMED, Set.of(COMPLETED, CANCELLED),
            COMPLETED, Set.of(DELIVERED, CANCELLED),
            DELIVERED, Set.of(),
            CANCELLED, Set.of());

    public Set<OrderStatus> allowedTransitions() {
        return ALLOWED.get(this);
    }

    public boolean canTransitionTo(OrderStatus target) {
        return ALLOWED.get(this).contains(target);
    }
}
