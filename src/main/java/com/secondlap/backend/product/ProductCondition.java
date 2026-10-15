package com.secondlap.backend.product;

public enum ProductCondition {
    COMO_NUEVO("Como nuevo"),
    EXCELENTE("Excelente"),
    BUEN_ESTADO("Buen estado");

    private final String value;

    ProductCondition(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ProductCondition fromValue(String value) {
        for (ProductCondition c : values()) {
            if (c.value.equals(value)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Unknown product condition: " + value);
    }
}
