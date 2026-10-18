package com.secondlap.backend.order;

import com.secondlap.backend.AbstractPostgresIntegrationTest;
import com.secondlap.backend.customer.CustomerRepository;
import com.secondlap.backend.product.Product;
import com.secondlap.backend.product.ProductRepository;
import com.secondlap.backend.product.ProductStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CreateOrderIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private String availableProductId() {
        return productRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProductStatus.DISPONIBLE)
                .map(p -> p.getPublicId().toString())
                .findFirst()
                .orElseThrow();
    }

    private String reservedProductId() {
        return productRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProductStatus.APARTADO)
                .map(p -> p.getPublicId().toString())
                .findFirst()
                .orElseThrow();
    }

    @Test
    void createsOrderForPickupAndComputesTotalsServerSide() throws Exception {
        String productId = availableProductId();
        double price = productRepository.findByPublicId(java.util.UUID.fromString(productId))
                .orElseThrow().getPrice().doubleValue();

        String body = """
                {
                  "productIds": ["%s"],
                  "customer": {"name": "Ana", "whatsapp": "(999) 123-4567"},
                  "deliveryMethod": "recoger",
                  "paymentMethod": "efectivo"
                }
                """.formatted(productId);

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status", org.hamcrest.Matchers.is("pendiente")))
                .andExpect(jsonPath("$.deliveryMethod", org.hamcrest.Matchers.is("recoger")))
                .andExpect(jsonPath("$.address").doesNotExist())
                .andExpect(jsonPath("$.shippingFee").value(numericallyEquals(0.0)))
                .andExpect(jsonPath("$.subtotal").value(numericallyEquals(price)))
                .andExpect(jsonPath("$.total").value(numericallyEquals(price)))
                .andExpect(jsonPath("$.storeWhatsapp", org.hamcrest.Matchers.is("529996384026")))
                .andExpect(jsonPath("$.customer.whatsapp", org.hamcrest.Matchers.is("9991234567")));

        // product must now be reserved in the DB (DB is the source of truth)
        Product reloaded = productRepository.findByPublicId(java.util.UUID.fromString(productId)).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ProductStatus.APARTADO);
    }

    @Test
    void returnsConflictWhenProductAlreadyReserved() throws Exception {
        String body = """
                {
                  "productIds": ["%s"],
                  "customer": {"name": "Beto", "whatsapp": "9995550000"},
                  "deliveryMethod": "recoger",
                  "paymentMethod": "efectivo"
                }
                """.formatted(reservedProductId());

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", org.hamcrest.Matchers.is("PRODUCT_NOT_AVAILABLE")))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.is("Esta prenda ya fue apartada")));
    }

    @Test
    void deduplicatesCustomerByWhatsappAcrossOrders() throws Exception {
        long before = customerRepository.count();
        List<String> two = productRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProductStatus.DISPONIBLE)
                .map(p -> p.getPublicId().toString())
                .limit(2)
                .toList();

        for (String productId : two) {
            String body = """
                    {
                      "productIds": ["%s"],
                      "customer": {"name": "Cora", "whatsapp": "9998887777"},
                      "deliveryMethod": "recoger",
                      "paymentMethod": "efectivo"
                    }
                    """.formatted(productId);
            mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated());
        }

        // same whatsapp across two orders => exactly one new customer
        assertThat(customerRepository.count()).isEqualTo(before + 1);
    }

    @Test
    void reusesCustomerByWhatsappAndUpdatesNameWhenItChanges() throws Exception {
        java.util.List<String> two = productRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProductStatus.DISPONIBLE)
                .map(p -> p.getPublicId().toString())
                .limit(2)
                .toList();

        String first = """
                {"productIds":["%s"],"customer":{"name":"Nombre Viejo","whatsapp":"9993332211"},
                 "deliveryMethod":"recoger","paymentMethod":"efectivo"}
                """.formatted(two.get(0));
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(first))
                .andExpect(status().isCreated());

        String second = """
                {"productIds":["%s"],"customer":{"name":"Nombre Nuevo","whatsapp":"9993332211"},
                 "deliveryMethod":"recoger","paymentMethod":"efectivo"}
                """.formatted(two.get(1));
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(second))
                .andExpect(status().isCreated());

        assertThat(customerRepository.findByWhatsapp("9993332211").orElseThrow().getName())
                .isEqualTo("Nombre Nuevo");
    }

    @Test
    void rejectsPickupOrderThatIncludesAddress() throws Exception {
        String body = """
                {
                  "productIds": ["%s"],
                  "customer": {"name": "Dani", "whatsapp": "9994443333"},
                  "deliveryMethod": "recoger",
                  "paymentMethod": "efectivo",
                  "address": {"street": "x", "neighborhood": "Centro", "city": "Mérida", "postalCode": "97000"}
                }
                """.formatted(availableProductId());

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsHomeDeliveryOrderResolvingShippingFeeFromNeighborhood() throws Exception {
        String productId = availableProductId();
        double price = productRepository.findByPublicId(java.util.UUID.fromString(productId))
                .orElseThrow().getPrice().doubleValue();

        // 'Caucel' ships with fee 30 in the real seed.
        String body = """
                {
                  "productIds": ["%s"],
                  "customer": {"name": "Fer", "whatsapp": "9992223344"},
                  "deliveryMethod": "domicilio",
                  "paymentMethod": "transferencia",
                  "address": {
                    "street": "Calle 1", "betweenStreets": "2 y 4", "neighborhood": "Caucel",
                    "city": "Mérida", "state": "Yucatán", "postalCode": "97314",
                    "references": "casa azul", "mapsLink": "https://maps.example/x"
                  }
                }
                """.formatted(productId);

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deliveryMethod", org.hamcrest.Matchers.is("domicilio")))
                .andExpect(jsonPath("$.address.neighborhood", org.hamcrest.Matchers.is("Caucel")))
                .andExpect(jsonPath("$.address.references", org.hamcrest.Matchers.is("casa azul")))
                .andExpect(jsonPath("$.address.shippingFee").value(numericallyEquals(30.0)))
                .andExpect(jsonPath("$.shippingFee").value(numericallyEquals(30.0)))
                .andExpect(jsonPath("$.subtotal").value(numericallyEquals(price)))
                .andExpect(jsonPath("$.total").value(numericallyEquals(price + 30.0)));
    }

    @Test
    void rejectsHomeDeliveryWithoutAddress() throws Exception {
        String body = """
                {
                  "productIds": ["%s"],
                  "customer": {"name": "Gus", "whatsapp": "9991110000"},
                  "deliveryMethod": "domicilio",
                  "paymentMethod": "tarjeta"
                }
                """.formatted(availableProductId());

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundForUnknownProductInOrder() throws Exception {
        String body = """
                {
                  "productIds": ["00000000-0000-0000-0000-000000000000"],
                  "customer": {"name": "Hugo", "whatsapp": "9995554444"},
                  "deliveryMethod": "recoger",
                  "paymentMethod": "efectivo"
                }
                """;

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", org.hamcrest.Matchers.is("NOT_FOUND")));
    }

    @Test
    void returnsNotFoundWhenOneOfSeveralProductsIsUnknown() throws Exception {
        String body = """
                {
                  "productIds": ["%s", "00000000-0000-0000-0000-000000000000"],
                  "customer": {"name": "Mix", "whatsapp": "9997778888"},
                  "deliveryMethod": "recoger",
                  "paymentMethod": "efectivo"
                }
                """.formatted(availableProductId());

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", org.hamcrest.Matchers.is("NOT_FOUND")));
    }

    @Test
    void rejectsOrderWithNoProducts() throws Exception {
        String body = """
                {
                  "productIds": [],
                  "customer": {"name": "Ivo", "whatsapp": "9996667777"},
                  "deliveryMethod": "recoger",
                  "paymentMethod": "efectivo"
                }
                """;

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", org.hamcrest.Matchers.is("VALIDATION_ERROR")));
    }

    @Test
    void rejectsOrderWithNonUuidProductId() throws Exception {
        // a non-UUID productId (e.g. a stale cart id) makes Jackson fail to parse
        // the body: it must be a 400 MALFORMED_REQUEST, not a 500
        String body = """
                {
                  "productIds": ["ply-002"],
                  "customer": {"name": "Jon", "whatsapp": "9990001122"},
                  "deliveryMethod": "recoger",
                  "paymentMethod": "efectivo"
                }
                """;

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", org.hamcrest.Matchers.is("MALFORMED_REQUEST")));
    }

    @Test
    void homeDeliveryWithUnknownNeighborhoodHasNoShippingFee() throws Exception {
        String productId = availableProductId();
        double price = productRepository.findByPublicId(java.util.UUID.fromString(productId))
                .orElseThrow().getPrice().doubleValue();
        String body = """
                {
                  "productIds": ["%s"],
                  "customer": {"name": "Zoe", "whatsapp": "9991239876"},
                  "deliveryMethod": "paqueteria",
                  "paymentMethod": "transferencia",
                  "address": {
                    "street": "Calle X", "neighborhood": "Colonia Inexistente 9999",
                    "city": "Mérida", "postalCode": "97000"
                  }
                }
                """.formatted(productId);

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.address.shippingFee").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.shippingFee").value(numericallyEquals(0.0)))
                .andExpect(jsonPath("$.total").value(numericallyEquals(price)));
    }

    // JSON numbers come back as Integer or Double depending on BigDecimal scale
    // (0 vs 320.00); compare by numeric value so assertions are scale-agnostic.
    private static org.hamcrest.Matcher<Object> numericallyEquals(double expected) {
        return new org.hamcrest.BaseMatcher<>() {
            @Override
            public boolean matches(Object actual) {
                return actual instanceof Number n && Double.compare(n.doubleValue(), expected) == 0;
            }

            @Override
            public void describeTo(org.hamcrest.Description description) {
                description.appendText("a number equal to ").appendValue(expected);
            }
        };
    }
}
