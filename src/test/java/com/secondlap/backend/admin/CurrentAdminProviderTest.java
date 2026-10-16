package com.secondlap.backend.admin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentAdminProviderTest {

    private final CurrentAdminProvider provider = new CurrentAdminProvider();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsNullWhenNoAuthentication() {
        assertThat(provider.username()).isNull();
    }

    @Test
    void returnsUsernameWhenAuthenticated() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "admin", "x", AuthorityUtils.createAuthorityList("ROLE_ADMIN")));

        assertThat(provider.username()).isEqualTo("admin");
    }

    @Test
    void returnsNullWhenAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken(
                        "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(provider.username()).isNull();
    }
}
