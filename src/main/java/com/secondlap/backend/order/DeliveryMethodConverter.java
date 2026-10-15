package com.secondlap.backend.order;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class DeliveryMethodConverter implements AttributeConverter<DeliveryMethod, String> {

    @Override
    public String convertToDatabaseColumn(DeliveryMethod attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public DeliveryMethod convertToEntityAttribute(String dbData) {
        return dbData == null ? null : DeliveryMethod.fromValue(dbData);
    }
}
