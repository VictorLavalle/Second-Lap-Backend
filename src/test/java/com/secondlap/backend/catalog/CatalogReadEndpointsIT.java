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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
        // 8 seeded products fit within the default page size (12)
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(8)))
                .andExpect(jsonPath("$.totalElements", is(8)))
                .andExpect(jsonPath("$.number", is(0)))
                .andExpect(jsonPath("$.size", is(12)))
                .andExpect(jsonPath("$.last", is(true)))
                // price must be a JSON number, never a quoted string
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"price\":120")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("\"price\":\"120"))));
    }

    @Test
    void paginatesProducts() throws Exception {
        // first page of 5: full page, not the last
        mockMvc.perform(get("/api/v1/products").param("pageSize", "5").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.totalElements", is(8)))
                .andExpect(jsonPath("$.totalPages", is(2)))
                .andExpect(jsonPath("$.number", is(0)))
                .andExpect(jsonPath("$.last", is(false)));

        // last page: remaining 3, last=true
        mockMvc.perform(get("/api/v1/products").param("pageSize", "5").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.number", is(1)))
                .andExpect(jsonPath("$.last", is(true)));
    }

    @Test
    void clampsInvalidPageAndSize() throws Exception {
        // page<0 -> 0; size>60 -> 60 (still one page for 8 products); no 500
        mockMvc.perform(get("/api/v1/products").param("page", "-1").param("pageSize", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number", is(0)))
                .andExpect(jsonPath("$.size", is(60)))
                .andExpect(jsonPath("$.content", hasSize(8)));

        // size<1 -> default 12
        mockMvc.perform(get("/api/v1/products").param("pageSize", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", is(12)));
    }

    @Test
    void filtersProductsByStatus() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("status", "apartado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].name", is("Pantalón de vestir gris")))
                .andExpect(jsonPath("$.content[0].status", is("apartado")));
    }

    @Test
    void filtersProductsByMaxPrice() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("maxPrice", "150"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", is(2)));
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

        // 'Playera estampada vintage' is a Playeras product; its similar set must be
        // other available Playeras, excluding itself.
        String playeraId = com.jayway.jsonpath.JsonPath.parse(listJson)
                .read("$.content[?(@.name == 'Playera estampada vintage')].id", java.util.List.class)
                .get(0).toString();

        // /similar is NOT paginated: still a flat array
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
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name", is("Jeans slim azul")));

        mockMvc.perform(get("/api/v1/products").param("sort", "price,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name", is("Playera básica blanca")));

        mockMvc.perform(get("/api/v1/products").param("sort", "price,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name", is("Tenis deportivos")));

        // single-token sort (no direction) and a blank size filter exercise the
        // default-direction and blank-guard branches.
        mockMvc.perform(get("/api/v1/products").param("sort", "name").param("size", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(8)));

        // blank sort falls back to the default ordering.
        mockMvc.perform(get("/api/v1/products").param("sort", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(8)));
    }

    @Test
    void similarFallsBackToDefaultLimitWhenNonPositive() throws Exception {
        String listJson = mockMvc.perform(get("/api/v1/products").param("status", "disponible"))
                .andReturn().getResponse().getContentAsString();
        String playeraId = com.jayway.jsonpath.JsonPath.parse(listJson)
                .read("$.content[?(@.name == 'Playera estampada vintage')].id", java.util.List.class)
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
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andReturn().getResponse().getContentAsString();

        String oneId = com.jayway.jsonpath.JsonPath.parse(productsJson).read("$.content[0].id", String.class);
        mockMvc.perform(get("/api/v1/products/{id}", oneId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(oneId)))
                .andExpect(jsonPath("$.categoryId", is(playerasCategoryId)));
    }

    @Test
    void filtersByMultipleSizesAndConditions() throws Exception {
        // sizes M (x2) OR L (x1) -> 3 products
        mockMvc.perform(get("/api/v1/products").param("size", "M").param("size", "L"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)));

        // conditions Excelente (x3) OR 'Como nuevo' (x2) -> 5
        mockMvc.perform(get("/api/v1/products")
                        .param("condition", "Excelente").param("condition", "Como nuevo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)));

        // AND across fields: size in {M,L} AND condition 'Buen estado' -> only the
        // 'Chamarra de mezclilla' (M, Buen estado); the L 'Playera estampada' is Buen estado too
        mockMvc.perform(get("/api/v1/products")
                        .param("size", "M").param("size", "L").param("condition", "Buen estado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));

        // CSV form also works (single param with commas)
        mockMvc.perform(get("/api/v1/products").param("size", "M,L"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)));

        // backward compatibility: a single value still works
        mockMvc.perform(get("/api/v1/products").param("size", "32"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name", is("Jeans slim azul")));

        // invalid condition value -> 400 (consistent with single-value behaviour)
        mockMvc.perform(get("/api/v1/products").param("condition", "Excelente").param("condition", "Inventada"))
                .andExpect(status().isBadRequest());

        // multi-filter combined with pagination + sort
        mockMvc.perform(get("/api/v1/products")
                        .param("size", "M").param("size", "L")
                        .param("sort", "price,asc").param("pageSize", "2").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.last", is(false)));
    }

    @Test
    void publicCatalogReadsCarryShortCacheControl() throws Exception {
        String expected = "max-age=15, public";
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", expected));
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", expected));
    }

    @Test
    void errorsOnPublicReadPathsAreNotPubliclyCached() throws Exception {
        mockMvc.perform(get("/api/v1/products/{id}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"));
        mockMvc.perform(get("/api/v1/products/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void returnsBadRequestForMalformedProductId() throws Exception {
        mockMvc.perform(get("/api/v1/products/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PARAMETER")));
    }

    @Test
    void responseCarriesRequestIdHeader() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().exists("X-Request-Id"));
    }

    @Test
    void sortsByWhitelistedDateFieldsAndFallsBackOnInvalid() throws Exception {
        // seeded products expose createdAt; sorting by it must succeed
        mockMvc.perform(get("/api/v1/products").param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(8)))
                .andExpect(jsonPath("$.content[0].createdAt", org.hamcrest.Matchers.notNullValue()));

        mockMvc.perform(get("/api/v1/products").param("sort", "modifiedDate,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(8)));

        // a non-whitelisted sort property must fall back to the default, never 500
        mockMvc.perform(get("/api/v1/products").param("sort", "name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(8)));

        mockMvc.perform(get("/api/v1/products").param("sort", "'; DROP TABLE product; --,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(8)));
    }
}
