package com.secondlap.backend.product;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ProductConditionConverter implements AttributeConverter<ProductCondition, String> {

    @Override
    public String convertToDatabaseColumn(ProductCondition attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public ProductCondition convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ProductCondition.fromValue(dbData);
    }
}
