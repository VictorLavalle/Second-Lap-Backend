package com.secondlap.backend.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * One access-log line per request: method, path, status and latency (the request
 * id is already in the MDC from RequestIdFilter). Business routes log at INFO;
 * health checks and static assets log at DEBUG so they are visible in dev (where
 * this logger runs at DEBUG) but silent in prod (INFO). Ordered after the
 * request-id and rate-limit filters so the id is present and the measured
 * latency covers the whole chain.
 */
@Component
@Order(2)
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AccessLogFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long millis = (System.nanoTime() - start) / 1_000_000;
            String line = "{} {} -> {} ({} ms)";
            Object[] args = {request.getMethod(), request.getRequestURI(), response.getStatus(), millis};
            if (isNoise(request.getRequestURI())) {
                log.debug(line, args);
            } else {
                log.info(line, args);
            }
        }
    }

    private boolean isNoise(String path) {
        return path.startsWith("/actuator/") || path.startsWith("/uploads/");
    }
}
