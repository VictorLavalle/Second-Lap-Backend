package com.secondlap.backend.admin;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserInitializerTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void createsAdminWhenMissing() {
        AdminUserRepository repo = mock(AdminUserRepository.class);
        when(repo.findByUsername("admin")).thenReturn(Optional.empty());

        new AdminUserInitializer(repo, encoder, new AdminProperties("admin", "secret"))
                .run(null);

        verify(repo).save(any(AdminUser.class));
    }

    @Test
    void refreshesHashWhenPasswordChanges() {
        AdminUserRepository repo = mock(AdminUserRepository.class);
        AdminUser existing = new AdminUser("admin", encoder.encode("old-password"));
        when(repo.findByUsername("admin")).thenReturn(Optional.of(existing));

        new AdminUserInitializer(repo, encoder, new AdminProperties("admin", "new-password"))
                .run(null);

        assertThat(encoder.matches("new-password", existing.getPasswordHash())).isTrue();
    }

    @Test
    void doesNothingWhenCredentialsAreBlank() {
        AdminUserRepository repo = mock(AdminUserRepository.class);

        new AdminUserInitializer(repo, encoder, new AdminProperties("admin", "  "))
                .run(null);

        verify(repo, never()).findByUsername(any());
        verify(repo, never()).save(any());
    }
}
