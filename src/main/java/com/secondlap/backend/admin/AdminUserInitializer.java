package com.secondlap.backend.admin;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provisions the admin account from environment variables at startup so no
 * credential is ever committed. Idempotent: creates the user if missing, and
 * refreshes the stored BCrypt hash when the configured password changes.
 */
@Component
public class AdminUserInitializer implements ApplicationRunner {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;

    public AdminUserInitializer(AdminUserRepository adminUserRepository,
                                PasswordEncoder passwordEncoder,
                                AdminProperties adminProperties) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminProperties = adminProperties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String username = adminProperties.username();
        String rawPassword = adminProperties.password();
        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            return;
        }

        adminUserRepository.findByUsername(username)
                .ifPresentOrElse(
                        existing -> refreshPasswordIfChanged(existing, rawPassword),
                        () -> adminUserRepository.save(
                                new AdminUser(username, passwordEncoder.encode(rawPassword))));
    }

    private void refreshPasswordIfChanged(AdminUser admin, String rawPassword) {
        if (!passwordEncoder.matches(rawPassword, admin.getPasswordHash())) {
            admin.setPasswordHash(passwordEncoder.encode(rawPassword));
        }
    }
}
