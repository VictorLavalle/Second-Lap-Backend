package com.secondlap.backend.admin;

import com.secondlap.backend.AbstractPostgresIntegrationTest;
import com.secondlap.backend.category.CategoryRepository;
import com.secondlap.backend.product.ProductRepository;
import com.secondlap.backend.product.ProductStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminEndpointsIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private com.secondlap.backend.admin.AdminUserInitializer adminUserInitializer;

    // The base class resets the DB before each test, which removes the admin
    // provisioned at startup; re-run the initializer so Basic auth works per test.
    @org.junit.jupiter.api.BeforeEach
    void provisionAdmin() {
        adminUserInitializer.run(null);
    }

    private String anyCategoryId() {
        return categoryRepository.findAll().get(0).getPublicId().toString();
    }

    private String availableProductId() {
        return productRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProductStatus.DISPONIBLE)
                .map(p -> p.getPublicId().toString())
                .findFirst()
                .orElseThrow();
    }

    @Test
    void adminEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsAuthenticatedAdminUsername() throws Exception {
        mockMvc.perform(get("/api/v1/admin/me").with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", org.hamcrest.Matchers.is("admin")));
    }

    @Test
    void meRejectsMissingCredentials() throws Exception {
        mockMvc.perform(get("/api/v1/admin/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meRejectsInvalidCredentials() throws Exception {
        mockMvc.perform(get("/api/v1/admin/me").with(httpBasic("admin", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createsProductWithBasicAuth() throws Exception {
        String body = """
                {
                  "name": "Suéter de lana",
                  "description": "Tejido grueso",
                  "price": 390,
                  "categoryId": "%s",
                  "size": "M",
                  "condition": "Excelente"
                }
                """.formatted(anyCategoryId());

        mockMvc.perform(post("/api/v1/admin/products")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", org.hamcrest.Matchers.is("Suéter de lana")))
                .andExpect(jsonPath("$.status", org.hamcrest.Matchers.is("disponible")));
    }

    @Test
    void rejectsProductCreationWithoutAuth() throws Exception {
        mockMvc.perform(post("/api/v1/admin/products")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadsProductImageToLocalDisk() throws Exception {
        String productId = availableProductId();
        MockMultipartFile image = new MockMultipartFile(
                "files", "foto.png", "image/png", new byte[]{10, 20, 30, 40});

        String responseBody = mockMvc.perform(multipart("/api/v1/admin/products/" + productId + "/images")
                        .file(image)
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // the stored URL points under /uploads and the file exists on local disk
        java.util.List<?> images = com.jayway.jsonpath.JsonPath.parse(responseBody)
                .read("$.images", java.util.List.class);
        String storedUrl = images.get(images.size() - 1).toString();
        assertThat(storedUrl).startsWith("/uploads/").endsWith(".png");

        String storedName = storedUrl.substring("/uploads/".length());
        Path onDisk = Path.of("uploads").resolve(storedName);
        assertThat(Files.exists(onDisk)).isTrue();
        Files.deleteIfExists(onDisk);
    }

    @Test
    void rejectsNonImageUpload() throws Exception {
        String productId = availableProductId();
        MockMultipartFile pdf = new MockMultipartFile(
                "files", "doc.pdf", "application/pdf", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/admin/products/" + productId + "/images")
                        .file(pdf)
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", org.hamcrest.Matchers.is("INVALID_UPLOAD")));
    }

    @Test
    void changesProductStatus() throws Exception {
        String productId = availableProductId();
        mockMvc.perform(patch("/api/v1/admin/products/" + productId + "/status")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"vendido\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", org.hamcrest.Matchers.is("vendido")));
    }

    @Test
    void cancellingOrderReleasesItemsBackToAvailable() throws Exception {
        String productId = availableProductId();

        // place an order (reserves the product)
        String orderBody = """
                {
                  "productIds": ["%s"],
                  "customer": {"name": "Eli", "whatsapp": "9990001111"},
                  "deliveryMethod": "recoger",
                  "paymentMethod": "efectivo"
                }
                """.formatted(productId);
        String orderResponse = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(orderBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = com.jayway.jsonpath.JsonPath.parse(orderResponse).read("$.id", String.class);

        assertThat(productRepository.findByPublicId(java.util.UUID.fromString(productId))
                .orElseThrow().getStatus()).isEqualTo(ProductStatus.APARTADO);

        // cancel it as admin -> product must be available again
        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/cancel")
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", org.hamcrest.Matchers.is("cancelled")));

        assertThat(productRepository.findByPublicId(java.util.UUID.fromString(productId))
                .orElseThrow().getStatus()).isEqualTo(ProductStatus.DISPONIBLE);
    }

    @Test
    void updatesAndDeletesProduct() throws Exception {
        String productId = availableProductId();
        String update = """
                {
                  "name": "Nombre actualizado",
                  "description": "desc",
                  "price": 199,
                  "categoryId": "%s",
                  "size": "L",
                  "condition": "Buen estado"
                }
                """.formatted(anyCategoryId());

        mockMvc.perform(put("/api/v1/admin/products/" + productId)
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", org.hamcrest.Matchers.is("Nombre actualizado")))
                .andExpect(jsonPath("$.size", org.hamcrest.Matchers.is("L")));

        mockMvc.perform(delete("/api/v1/admin/products/" + productId)
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isNoContent());

        assertThat(productRepository.findByPublicId(java.util.UUID.fromString(productId))).isEmpty();
    }

    @Test
    void returnsNotFoundUpdatingUnknownProduct() throws Exception {
        String update = """
                {"name":"x","description":"d","price":1,"categoryId":"%s","size":"M","condition":"Excelente"}
                """.formatted(anyCategoryId());
        mockMvc.perform(put("/api/v1/admin/products/00000000-0000-0000-0000-000000000000")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirmingOrderMarksItsProductsAsSold() throws Exception {
        String productId = availableProductId();
        String orderBody = """
                {
                  "productIds": ["%s"],
                  "customer": {"name": "Confirmador", "whatsapp": "9990009999"},
                  "deliveryMethod": "recoger",
                  "paymentMethod": "efectivo"
                }
                """.formatted(productId);
        String orderResponse = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(orderBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = com.jayway.jsonpath.JsonPath.parse(orderResponse).read("$.id", String.class);

        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/confirm")
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", org.hamcrest.Matchers.is("confirmed")));

        assertThat(productRepository.findByPublicId(java.util.UUID.fromString(productId))
                .orElseThrow().getStatus()).isEqualTo(ProductStatus.VENDIDO);
    }

    @Test
    void rejectsCancellingAConfirmedOrderWithoutResurrectingProducts() throws Exception {
        String productId = availableProductId();
        String orderBody = """
                {
                  "productIds": ["%s"],
                  "customer": {"name": "NoCancelar", "whatsapp": "9990002222"},
                  "deliveryMethod": "recoger",
                  "paymentMethod": "efectivo"
                }
                """.formatted(productId);
        String orderResponse = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(orderBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = com.jayway.jsonpath.JsonPath.parse(orderResponse).read("$.id", String.class);

        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/confirm")
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/cancel")
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", org.hamcrest.Matchers.is("INVALID_ORDER_TRANSITION")));

        // the confirmed order's product stays sold; cancel must not resurrect it
        assertThat(productRepository.findByPublicId(java.util.UUID.fromString(productId))
                .orElseThrow().getStatus()).isEqualTo(ProductStatus.VENDIDO);
    }

    @Test
    void rejectsConfirmingAnAlreadyCancelledOrder() throws Exception {
        String productId = availableProductId();
        String orderBody = """
                {
                  "productIds": ["%s"],
                  "customer": {"name": "NoConfirmar", "whatsapp": "9990003333"},
                  "deliveryMethod": "recoger",
                  "paymentMethod": "efectivo"
                }
                """.formatted(productId);
        String orderResponse = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(orderBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = com.jayway.jsonpath.JsonPath.parse(orderResponse).read("$.id", String.class);

        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/cancel")
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/confirm")
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", org.hamcrest.Matchers.is("INVALID_ORDER_TRANSITION")));

        // the cancelled order released its product; confirm must not sell it
        assertThat(productRepository.findByPublicId(java.util.UUID.fromString(productId))
                .orElseThrow().getStatus()).isEqualTo(ProductStatus.DISPONIBLE);
    }

    @Test
    void listsOrdersForAdminMostRecentFirst() throws Exception {
        String firstProduct = availableProductId();
        String firstBody = """
                {
                  "productIds": ["%s"],
                  "customer": {"name": "Primero", "whatsapp": "9990001234"},
                  "deliveryMethod": "recoger",
                  "paymentMethod": "efectivo"
                }
                """.formatted(firstProduct);
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON).content(firstBody)).andExpect(status().isCreated());

        String secondProduct = availableProductId();
        String secondBody = """
                {
                  "productIds": ["%s"],
                  "customer": {"name": "Segundo", "whatsapp": "9990005678"},
                  "deliveryMethod": "recoger",
                  "paymentMethod": "efectivo"
                }
                """.formatted(secondProduct);
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON).content(secondBody)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/admin/orders").with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].customer.whatsapp", org.hamcrest.Matchers.is("9990005678")))
                .andExpect(jsonPath("$[1].customer.whatsapp", org.hamcrest.Matchers.is("9990001234")));
    }

    @Test
    void rejectsWrongAdminPassword() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders").with(httpBasic("admin", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsNotFoundCreatingProductWithUnknownCategory() throws Exception {
        String body = """
                {"name":"X","description":"d","price":10,
                 "categoryId":"00000000-0000-0000-0000-000000000000","size":"M","condition":"Excelente"}
                """;
        mockMvc.perform(post("/api/v1/admin/products")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundCancellingUnknownOrder() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/orders/00000000-0000-0000-0000-000000000000/cancel")
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isNotFound());
    }
}
