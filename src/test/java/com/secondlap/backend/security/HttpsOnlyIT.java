package com.secondlap.backend.security;

import com.secondlap.backend.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = "secondlap.security.require-https=true")
class HttpsOnlyIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void redirectsPlainHttpToHttpsWhenRequireHttpsEnabled() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().is3xxRedirection());
    }
}
