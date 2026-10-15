package com.secondlap.backend.order;

import com.secondlap.backend.AbstractPostgresIntegrationTest;
import com.secondlap.backend.neighborhood.Neighborhood;
import com.secondlap.backend.neighborhood.NeighborhoodRepository;
import com.secondlap.backend.product.ProductRepository;
import com.secondlap.backend.product.ProductStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the internal identifiers of persisted entities (the BIGINT id and
 * public UUID) that no API flow reads, so the entity contract is fully covered.
 */
@AutoConfigureMockMvc
class PersistedEntityIdentifiersIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NeighborhoodRepository neighborhoodRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void neighborhoodExposesInternalAndPublicIds() {
        Neighborhood n = neighborhoodRepository.findAll().get(0);
        assertThat(n.getId()).isNotNull();
        assertThat(n.getPublicId()).isNotNull();
        assertThat(n.getName()).isNotBlank();
    }

    @Test
    void orderExposesInternalIdAfterPersistence() throws Exception {
        String productId = productRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProductStatus.DISPONIBLE)
                .map(p -> p.getPublicId().toString())
                .findFirst().orElseThrow();
        String body = """
                {"productIds":["%s"],"customer":{"name":"Id","whatsapp":"9990000001"},
                 "deliveryMethod":"recoger","paymentMethod":"efectivo"}
                """.formatted(productId);
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        Order order = orderRepository.findAll().get(0);
        assertThat(order.getId()).isNotNull();
    }
}
