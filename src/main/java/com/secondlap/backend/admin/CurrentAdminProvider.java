package com.secondlap.backend.admin;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Single place that reads the authenticated admin username from the security
 * context. Isolating it here keeps the ThreadLocal coupling of
 * SecurityContextHolder in one testable spot and returns null when there is no
 * authenticated admin (anonymous or no context, e.g. the public checkout flow).
 */
@Component
public class CurrentAdminProvider {

    public String username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return auth.getName();
    }
}
