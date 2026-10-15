package com.secondlap.backend.security;

import com.secondlap.backend.AbstractPostgresIntegrationTest;
import com.secondlap.backend.product.ProductRepository;
import com.secondlap.backend.product.ProductStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SecurityHardeningIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private com.secondlap.backend.admin.AdminUserInitializer adminUserInitializer;

    // The base class resets the DB before each test, which removes the admin
    // provisioned at startup; re-run the initializer so Basic auth works.
    @org.junit.jupiter.api.BeforeEach
    void provisionAdmin() {
        adminUserInitializer.run(null);
    }

    private String availableProductId() {
        return productRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProductStatus.DISPONIBLE)
                .map(p -> p.getPublicId().toString())
                .findFirst().orElseThrow();
    }

    @Test
    void responsesCarrySecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"));
    }

    @Test
    void rejectsOrderWithTooManyItems() throws Exception {
        String ids = java.util.stream.IntStream.range(0, 21)
                .mapToObj(i -> "\"" + UUID.randomUUID() + "\"")
                .collect(Collectors.joining(","));
        String body = """
                {"productIds":[%s],"deliveryMethod":"recoger","paymentMethod":"efectivo",
                 "customer":{"name":"Many","whatsapp":"9992223344"}}
                """.formatted(ids);

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-Forwarded-For", "203.0.113.20")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", org.hamcrest.Matchers.is("BAD_REQUEST")));
    }

    @Test
    void rejectsOversizedImageUpload() throws Exception {
        byte[] tooBig = new byte[6 * 1024 * 1024]; // 6 MB > 5 MB limit
        MockMultipartFile big = new MockMultipartFile("files", "big.png", "image/png", tooBig);

        // The oversized upload is rejected by size. Through MockMvc this surfaces
        // as 400 from the storage validator; against a real servlet container the
        // multipart limit returns 413 earlier (verified manually with curl).
        mockMvc.perform(multipart("/api/v1/admin/products/" + availableProductId() + "/images")
                        .file(big)
                        .header("X-Forwarded-For", "203.0.113.30")
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code", org.hamcrest.Matchers.oneOf("INVALID_UPLOAD", "PAYLOAD_TOO_LARGE")));
    }
}
