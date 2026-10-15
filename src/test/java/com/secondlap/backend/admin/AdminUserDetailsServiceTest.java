package com.secondlap.backend.admin;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminUserDetailsServiceTest {

    @Test
    void loadsExistingAdminWithAdminRole() {
        AdminUserRepository repo = mock(AdminUserRepository.class);
        when(repo.findByUsername("admin")).thenReturn(Optional.of(new AdminUser("admin", "hash")));

        UserDetails details = new AdminUserDetailsService(repo).loadUserByUsername("admin");

        assertThat(details.getUsername()).isEqualTo("admin");
        assertThat(details.getPassword()).isEqualTo("hash");
        assertThat(details.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @Test
    void throwsForUnknownAdmin() {
        AdminUserRepository repo = mock(AdminUserRepository.class);
        when(repo.findByUsername("ghost")).thenReturn(Optional.empty());

        AdminUserDetailsService service = new AdminUserDetailsService(repo);
        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
