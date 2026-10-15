package com.secondlap.backend.order;

public enum PaymentMethod {
    TRANSFERENCIA("transferencia"),
    EFECTIVO("efectivo"),
    TARJETA("tarjeta");

    private final String value;

    PaymentMethod(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static PaymentMethod fromValue(String value) {
        for (PaymentMethod m : values()) {
            if (m.value.equals(value)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Unknown payment method: " + value);
    }
}
