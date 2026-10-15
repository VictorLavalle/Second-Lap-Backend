package com.secondlap.backend.common;

public class ProductNotAvailableException extends RuntimeException {

    private final String productId;

    public ProductNotAvailableException(String productId) {
        super("Esta prenda ya fue apartada");
        this.productId = productId;
    }

    public String getProductId() {
        return productId;
    }
}
