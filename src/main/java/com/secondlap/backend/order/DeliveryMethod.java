package com.secondlap.backend.order;

public enum DeliveryMethod {
    RECOGER("recoger"),
    DOMICILIO("domicilio"),
    PAQUETERIA("paqueteria");

    private final String value;

    DeliveryMethod(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public boolean requiresAddress() {
        return this != RECOGER;
    }

    public static DeliveryMethod fromValue(String value) {
        for (DeliveryMethod m : values()) {
            if (m.value.equals(value)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Unknown delivery method: " + value);
    }
}
