package com.secondlap.backend;

import com.secondlap.backend.order.DeliveryMethod;
import com.secondlap.backend.order.DeliveryMethodConverter;
import com.secondlap.backend.order.OrderStatus;
import com.secondlap.backend.order.OrderStatusConverter;
import com.secondlap.backend.order.PaymentMethod;
import com.secondlap.backend.order.PaymentMethodConverter;
import com.secondlap.backend.product.ProductCondition;
import com.secondlap.backend.product.ProductConditionConverter;
import com.secondlap.backend.product.ProductStatus;
import com.secondlap.backend.product.ProductStatusConverter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnumConvertersTest {

    @Test
    void productStatusConverterRoundTrips() {
        ProductStatusConverter c = new ProductStatusConverter();
        assertThat(c.convertToDatabaseColumn(ProductStatus.APARTADO)).isEqualTo("apartado");
        assertThat(c.convertToDatabaseColumn(null)).isNull();
        assertThat(c.convertToEntityAttribute("vendido")).isEqualTo(ProductStatus.VENDIDO);
        assertThat(c.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void productConditionConverterRoundTrips() {
        ProductConditionConverter c = new ProductConditionConverter();
        assertThat(c.convertToDatabaseColumn(ProductCondition.COMO_NUEVO)).isEqualTo("Como nuevo");
        assertThat(c.convertToDatabaseColumn(null)).isNull();
        assertThat(c.convertToEntityAttribute("Excelente")).isEqualTo(ProductCondition.EXCELENTE);
        assertThat(c.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void orderStatusConverterRoundTrips() {
        OrderStatusConverter c = new OrderStatusConverter();
        assertThat(c.convertToDatabaseColumn(OrderStatus.CONFIRMED)).isEqualTo("confirmed");
        assertThat(c.convertToDatabaseColumn(null)).isNull();
        assertThat(c.convertToEntityAttribute("cancelled")).isEqualTo(OrderStatus.CANCELLED);
        assertThat(c.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void paymentMethodConverterRoundTrips() {
        PaymentMethodConverter c = new PaymentMethodConverter();
        assertThat(c.convertToDatabaseColumn(PaymentMethod.EFECTIVO)).isEqualTo("efectivo");
        assertThat(c.convertToDatabaseColumn(null)).isNull();
        assertThat(c.convertToEntityAttribute("tarjeta")).isEqualTo(PaymentMethod.TARJETA);
        assertThat(c.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void deliveryMethodConverterRoundTrips() {
        DeliveryMethodConverter c = new DeliveryMethodConverter();
        assertThat(c.convertToDatabaseColumn(DeliveryMethod.PAQUETERIA)).isEqualTo("paqueteria");
        assertThat(c.convertToDatabaseColumn(null)).isNull();
        assertThat(c.convertToEntityAttribute("domicilio")).isEqualTo(DeliveryMethod.DOMICILIO);
        assertThat(c.convertToEntityAttribute(null)).isNull();
    }
}
