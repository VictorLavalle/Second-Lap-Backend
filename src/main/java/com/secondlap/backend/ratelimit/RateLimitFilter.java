package com.secondlap.backend.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondlap.backend.common.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Per-IP rate limiting applied before authentication. Orders are throttled
 * strictly (they reserve unique inventory), admin login aggressively (brute
 * force), and public reads loosely. Volumetric DDoS is out of scope here and is
 * handled upstream (CDN/WAF).
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitService rateLimitService, ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ip = clientIp(request);
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (!isAllowed(path, method, ip)) {
            writeTooManyRequests(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isAllowed(String path, String method, String ip) {
        if (path.startsWith("/api/v1/admin/")) {
            return rateLimitService.tryConsumeAdminLogin(ip);
        }
        if ("POST".equals(method) && path.equals("/api/v1/orders")) {
            return rateLimitService.tryConsumeOrderByIp(ip);
        }
        if ("GET".equals(method) && path.startsWith("/api/v1/")) {
            return rateLimitService.tryConsumePublicRead(ip);
        }
        return true;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, "60");
        ApiError error = ApiError.of("RATE_LIMITED", "Demasiadas solicitudes, intenta más tarde");
        objectMapper.writeValue(response.getWriter(), error);
    }
}
