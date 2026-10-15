package com.secondlap.backend;

import com.secondlap.backend.order.DeliveryMethod;
import com.secondlap.backend.order.OrderStatus;
import com.secondlap.backend.order.PaymentMethod;
import com.secondlap.backend.product.ProductCondition;
import com.secondlap.backend.product.ProductStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainEnumsTest {

    @Test
    void resolvesSpanishValues() {
        assertThat(ProductCondition.fromValue("Como nuevo")).isEqualTo(ProductCondition.COMO_NUEVO);
        assertThat(ProductStatus.fromValue("apartado")).isEqualTo(ProductStatus.APARTADO);
        assertThat(DeliveryMethod.fromValue("domicilio")).isEqualTo(DeliveryMethod.DOMICILIO);
        assertThat(PaymentMethod.fromValue("transferencia")).isEqualTo(PaymentMethod.TRANSFERENCIA);
        assertThat(OrderStatus.fromValue("confirmed")).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void deliveryMethodKnowsWhenAddressIsRequired() {
        assertThat(DeliveryMethod.RECOGER.requiresAddress()).isFalse();
        assertThat(DeliveryMethod.DOMICILIO.requiresAddress()).isTrue();
        assertThat(DeliveryMethod.PAQUETERIA.requiresAddress()).isTrue();
    }

    @Test
    void rejectsUnknownValues() {
        assertThatThrownBy(() -> ProductCondition.fromValue("x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProductStatus.fromValue("x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeliveryMethod.fromValue("x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PaymentMethod.fromValue("x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OrderStatus.fromValue("x")).isInstanceOf(IllegalArgumentException.class);
    }
}
