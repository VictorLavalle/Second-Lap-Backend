package com.secondlap.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Owns the Cache-Control header for the API. Spring Security's default
 * cache-control writer is disabled so this filter is the single source: public
 * catalog GETs get a short public TTL so a CDN/proxy can absorb the storefront
 * polling, and every other response gets the safe no-store default Security used
 * to emit. Errors on the public read paths are downgraded back to no-store by
 * the global exception handler (which runs before the response is committed).
 */
public class PublicReadCacheFilter extends OncePerRequestFilter {

    private static final String NO_STORE = CacheControl.noStore().getHeaderValue();
    private static final List<String> PUBLIC_READ_PATTERNS = List.of(
            "/api/v1/products",
            "/api/v1/products/*",
            "/api/v1/products/*/similar",
            "/api/v1/categories");

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final String publicValue;

    public PublicReadCacheFilter(long maxAgeSeconds) {
        this.publicValue = CacheControl.maxAge(Duration.ofSeconds(maxAgeSeconds))
                .cachePublic()
                .getHeaderValue();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, isPublicRead(request) ? publicValue : NO_STORE);
        chain.doFilter(request, response);
    }

    private boolean isPublicRead(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        return PUBLIC_READ_PATTERNS.stream().anyMatch(pattern -> pathMatcher.match(pattern, uri));
    }
}
