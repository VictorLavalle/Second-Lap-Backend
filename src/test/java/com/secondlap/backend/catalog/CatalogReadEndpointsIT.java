package com.secondlap.backend.catalog;

import com.secondlap.backend.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CatalogReadEndpointsIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsCategoriesSortedByOrder() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].name", is("Playeras")))
                .andExpect(jsonPath("$[0].order", is(1)));
    }

    @Test
    void listsAllNeighborhoods() throws Exception {
        mockMvc.perform(get("/api/v1/neighborhoods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(165)));
    }

    @Test
    void listsAllProductsAndSerializesPriceAsNumber() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                // price must be a JSON number, never a quoted string
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"price\":120")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("\"price\":\"120"))));
    }

    @Test
    void filtersProductsByStatus() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("status", "apartado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Pantalón de vestir gris")))
                .andExpect(jsonPath("$[0].status", is("apartado")));
    }

    @Test
    void filtersProductsByMaxPrice() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("maxPrice", "150"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void returnsNotFoundForUnknownProduct() throws Exception {
        mockMvc.perform(get("/api/v1/products/{id}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")));
    }

    @Test
    void returnsSimilarProductsFromSameCategoryExcludingItself() throws Exception {
        String listJson = mockMvc.perform(get("/api/v1/products").param("status", "disponible"))
                .andReturn().getResponse().getContentAsString();

        // 'Playera básica blanca' is a Playeras product; its similar set must be
        // other available Playeras, excluding itself.
        String playeraId = com.jayway.jsonpath.JsonPath.parse(listJson)
                .read("$[?(@.name == 'Playera estampada vintage')].id", java.util.List.class)
                .get(0).toString();

        mockMvc.perform(get("/api/v1/products/{id}/similar", playeraId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[?(@.id == '" + playeraId + "')]", hasSize(0)));
    }

    @Test
    void filtersBySizeAndConditionAndSortsByPrice() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("size", "32").param("condition", "Como nuevo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Jeans slim azul")));

        mockMvc.perform(get("/api/v1/products").param("sort", "price,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name", is("Playera básica blanca")));

        mockMvc.perform(get("/api/v1/products").param("sort", "price,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name", is("Tenis deportivos")));

        // single-token sort (no direction) and a blank size filter exercise the
        // default-direction and blank-guard branches.
        mockMvc.perform(get("/api/v1/products").param("sort", "name").param("size", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)));

        // blank sort falls back to the default ordering.
        mockMvc.perform(get("/api/v1/products").param("sort", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)));
    }

    @Test
    void similarFallsBackToDefaultLimitWhenNonPositive() throws Exception {
        String listJson = mockMvc.perform(get("/api/v1/products").param("status", "disponible"))
                .andReturn().getResponse().getContentAsString();
        String playeraId = com.jayway.jsonpath.JsonPath.parse(listJson)
                .read("$[?(@.name == 'Playera estampada vintage')].id", java.util.List.class)
                .get(0).toString();

        mockMvc.perform(get("/api/v1/products/{id}/similar", playeraId).param("limit", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void filtersByCategoryUuidAndFetchesSingleProduct() throws Exception {
        String listJson = mockMvc.perform(get("/api/v1/categories"))
                .andReturn().getResponse().getContentAsString();
        String playerasCategoryId = com.jayway.jsonpath.JsonPath.parse(listJson)
                .read("$[?(@.name == 'Playeras')].id", java.util.List.class).get(0).toString();

        String productsJson = mockMvc.perform(get("/api/v1/products").param("category", playerasCategoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andReturn().getResponse().getContentAsString();

        String oneId = com.jayway.jsonpath.JsonPath.parse(productsJson).read("$[0].id", String.class);
        mockMvc.perform(get("/api/v1/products/{id}", oneId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(oneId)))
                .andExpect(jsonPath("$.categoryId", is(playerasCategoryId)));
    }
}
