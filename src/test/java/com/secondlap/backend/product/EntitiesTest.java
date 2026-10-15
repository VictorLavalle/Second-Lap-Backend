package com.secondlap.backend.product;

import com.secondlap.backend.customer.Customer;
import com.secondlap.backend.order.OrderItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EntitiesTest {

    @Test
    void productExposesAttributesAndReplacesImages() {
        ProductDetails details = new ProductDetails(
                "Camisa", "desc", new BigDecimal("199.00"), null,
                "M", ProductCondition.EXCELENTE, "Zara", "Azul", "Largo 70cm");
        Product product = new Product(details);

        assertThat(product.getName()).isEqualTo("Camisa");
        assertThat(product.getDescription()).isEqualTo("desc");
        assertThat(product.getPrice()).isEqualByComparingTo("199.00");
        assertThat(product.getSize()).isEqualTo("M");
        assertThat(product.getCondition()).isEqualTo(ProductCondition.EXCELENTE);
        assertThat(product.getBrand()).isEqualTo("Zara");
        assertThat(product.getColor()).isEqualTo("Azul");
        assertThat(product.getMeasurements()).isEqualTo("Largo 70cm");
        assertThat(product.getStatus()).isEqualTo(ProductStatus.DISPONIBLE);

        product.addImage("/uploads/a.png");
        product.replaceImages(List.of("/uploads/x.png", "/uploads/y.png"));
        assertThat(product.getImages()).hasSize(2);
        assertThat(product.getImages().get(0).getUrl()).isEqualTo("/uploads/x.png");
        assertThat(product.getImages().get(1).getPosition()).isEqualTo(1);
    }

    @Test
    void productImageExposesItsFields() {
        Product product = new Product(new ProductDetails(
                "P", "d", BigDecimal.ONE, null, "S", ProductCondition.BUEN_ESTADO, null, null, null));
        ProductImage image = new ProductImage(product, "/uploads/z.png", 3);

        assertThat(image.getProduct()).isSameAs(product);
        assertThat(image.getUrl()).isEqualTo("/uploads/z.png");
        assertThat(image.getPosition()).isEqualTo(3);
        assertThat(image.getId()).isNull();
    }

    @Test
    void customerExposesItsFields() {
        Customer customer = new Customer("Ana", "9991234567", "sin timbre");

        assertThat(customer.getName()).isEqualTo("Ana");
        assertThat(customer.getWhatsapp()).isEqualTo("9991234567");
        assertThat(customer.getComments()).isEqualTo("sin timbre");
        assertThat(customer.getId()).isNull();
        assertThat(customer.getPublicId()).isNull();
    }

    @Test
    void orderItemExposesItsFields() {
        Product product = new Product(new ProductDetails(
                "P", "d", new BigDecimal("50.00"), null, "S", ProductCondition.EXCELENTE, null, null, null));
        OrderItem item = new OrderItem(null, product, new BigDecimal("50.00"));

        assertThat(item.getProduct()).isSameAs(product);
        assertThat(item.getPriceSnapshot()).isEqualByComparingTo("50.00");
        assertThat(item.getOrder()).isNull();
        assertThat(item.getId()).isNull();
        assertThat(item.getPublicId()).isNull();
    }
}
