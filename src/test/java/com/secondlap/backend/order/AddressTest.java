package com.secondlap.backend.order;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AddressTest {

    @Test
    void exposesItsFields() {
        Address address = new Address();
        address.setStreet("Calle 1");
        address.setBetweenStreets("2 y 4");
        address.setNeighborhood("Caucel");
        address.setCity("Mérida");
        address.setState("Yucatán");
        address.setPostalCode("97314");
        address.setReferenceNotes("casa azul");
        address.setMapsLink("https://maps.example/x");
        address.setShippingFee(new BigDecimal("30.00"));

        assertThat(address.getStreet()).isEqualTo("Calle 1");
        assertThat(address.getBetweenStreets()).isEqualTo("2 y 4");
        assertThat(address.getNeighborhood()).isEqualTo("Caucel");
        assertThat(address.getCity()).isEqualTo("Mérida");
        assertThat(address.getState()).isEqualTo("Yucatán");
        assertThat(address.getPostalCode()).isEqualTo("97314");
        assertThat(address.getReferenceNotes()).isEqualTo("casa azul");
        assertThat(address.getMapsLink()).isEqualTo("https://maps.example/x");
        assertThat(address.getShippingFee()).isEqualByComparingTo("30.00");
        assertThat(address.getId()).isNull();
        assertThat(address.getPublicId()).isNull();
    }
}
