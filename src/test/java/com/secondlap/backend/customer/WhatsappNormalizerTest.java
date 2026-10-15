package com.secondlap.backend.customer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhatsappNormalizerTest {

    @Test
    void stripsSeparatorsAndKeepsTenDigits() {
        assertThat(WhatsappNormalizer.normalize("(999) 123-4567")).isEqualTo("9991234567");
    }

    @Test
    void dropsCountryPrefixKeepingLastTenDigits() {
        assertThat(WhatsappNormalizer.normalize("+52 999 123 4567")).isEqualTo("9991234567");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> WhatsappNormalizer.normalize(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    @Test
    void rejectsFewerThanTenDigits() {
        assertThatThrownBy(() -> WhatsappNormalizer.normalize("123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10 digits");
    }
}
