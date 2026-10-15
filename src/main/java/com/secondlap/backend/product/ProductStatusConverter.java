package com.secondlap.backend.product;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ProductStatusConverter implements AttributeConverter<ProductStatus, String> {

    @Override
    public String convertToDatabaseColumn(ProductStatus attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public ProductStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ProductStatus.fromValue(dbData);
    }
}
