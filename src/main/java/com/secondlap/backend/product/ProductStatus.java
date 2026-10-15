package com.secondlap.backend.product;

public enum ProductStatus {
    DISPONIBLE("disponible"),
    APARTADO("apartado"),
    VENDIDO("vendido");

    private final String value;

    ProductStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ProductStatus fromValue(String value) {
        for (ProductStatus s : values()) {
            if (s.value.equals(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown product status: " + value);
    }
}
