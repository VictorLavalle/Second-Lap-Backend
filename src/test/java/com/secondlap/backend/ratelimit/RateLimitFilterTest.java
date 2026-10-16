package com.secondlap.backend.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void returns429WithApiErrorWhenOrderLimitExceeded() throws Exception {
        RateLimitService service = mock(RateLimitService.class);
        when(service.tryConsumeOrderByIp(any())).thenReturn(false);
        RateLimitFilter filter = new RateLimitFilter(service, mapper);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.setRemoteAddr("9.9.9.9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(response.getContentAsString()).contains("RATE_LIMITED");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void prefersForwardedForClientIp() throws Exception {
        RateLimitService service = mock(RateLimitService.class);
        when(service.tryConsumePublicRead("203.0.113.1")).thenReturn(true);
        RateLimitFilter filter = new RateLimitFilter(service, mapper);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(service).tryConsumePublicRead("203.0.113.1");
        verify(chain).doFilter(request, response);
    }

    @Test
    void passesThroughNonRateLimitedPaths() throws Exception {
        RateLimitService service = mock(RateLimitService.class);
        RateLimitFilter filter = new RateLimitFilter(service, mapper);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void throttlesAdminMeByLoginBucketUsingRemoteAddr() throws Exception {
        RateLimitService service = mock(RateLimitService.class);
        when(service.tryConsumeAdminLogin("8.8.8.8")).thenReturn(true);
        RateLimitFilter filter = new RateLimitFilter(service, mapper);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/me");
        request.setRemoteAddr("8.8.8.8");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(service).tryConsumeAdminLogin("8.8.8.8");
        verify(chain).doFilter(request, response);
    }

    @Test
    void throttlesOtherAdminPathsByAdminApiBucket() throws Exception {
        RateLimitService service = mock(RateLimitService.class);
        when(service.tryConsumeAdminApi("8.8.8.8")).thenReturn(true);
        RateLimitFilter filter = new RateLimitFilter(service, mapper);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/orders");
        request.setRemoteAddr("8.8.8.8");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(service).tryConsumeAdminApi("8.8.8.8");
        verify(service, never()).tryConsumeAdminLogin(any());
        verify(chain).doFilter(request, response);
    }

    @Test
    void deniesAdminWhenAdminApiBucketExhausted() throws Exception {
        RateLimitService service = mock(RateLimitService.class);
        when(service.tryConsumeAdminApi(any())).thenReturn(false);
        RateLimitFilter filter = new RateLimitFilter(service, mapper);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/products");
        request.setRemoteAddr("8.8.8.8");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void exhaustedLoginBucketDoesNotBlockAdminApiWork() throws Exception {
        RateLimitService service = mock(RateLimitService.class);
        when(service.tryConsumeAdminApi("8.8.8.8")).thenReturn(true);
        RateLimitFilter filter = new RateLimitFilter(service, mapper);

        // admin API work routes through the admin-api bucket, never login
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/products");
        request.setRemoteAddr("8.8.8.8");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(service).tryConsumeAdminApi("8.8.8.8");
        verify(service, never()).tryConsumeAdminLogin(any());
        verify(chain).doFilter(request, response);
    }

    @Test
    void doesNotThrottleNonOrderPostOrNonApiGet() throws Exception {
        RateLimitService service = mock(RateLimitService.class);
        RateLimitFilter filter = new RateLimitFilter(service, mapper);

        // POST to a path that is not /api/v1/orders is not throttled
        MockHttpServletRequest post = new MockHttpServletRequest("POST", "/api/v1/other");
        MockHttpServletResponse postResp = new MockHttpServletResponse();
        FilterChain postChain = mock(FilterChain.class);
        filter.doFilter(post, postResp, postChain);
        verify(postChain).doFilter(post, postResp);

        // GET outside /api/v1/ is not throttled
        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/uploads/x.png");
        MockHttpServletResponse getResp = new MockHttpServletResponse();
        FilterChain getChain = mock(FilterChain.class);
        filter.doFilter(get, getResp, getChain);
        verify(getChain).doFilter(get, getResp);
    }

    @Test
    void fallsBackToRemoteAddrWhenForwardedForIsBlank() throws Exception {
        RateLimitService service = mock(RateLimitService.class);
        when(service.tryConsumePublicRead("7.7.7.7")).thenReturn(true);
        RateLimitFilter filter = new RateLimitFilter(service, mapper);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.addHeader("X-Forwarded-For", "   ");
        request.setRemoteAddr("7.7.7.7");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(service).tryConsumePublicRead("7.7.7.7");
    }
}
