package com.secondlap.backend.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Assigns a request id to every request so logs, the X-Request-Id response
 * header and the correlationId returned on a 500 all share one id. Ordered
 * before the rate-limit filter so even 429 responses carry the id.
 */
@Component
@Order(0)
public class RequestIdFilter extends OncePerRequestFilter {

    static final String MDC_KEY = "requestId";
    static final String HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String incoming = request.getHeader(HEADER);
        return incoming != null && !incoming.isBlank() ? incoming : UUID.randomUUID().toString();
    }
}
