package com.ragbot.config.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragbot.application.service.RateLimiterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimitFilter")
class RateLimitFilterTest {

    // anon: 5/min, 50/dia (mesmos defaults do application.yml)
    private final RateLimiterService limiter = new RateLimiterService(20, 500, 5, 50);
    private final RateLimitFilter filter = new RateLimitFilter(limiter, new ObjectMapper());

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private int login(String remoteAddr, String forwardedFor) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) req.addHeader("X-Forwarded-For", forwardedFor);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res.getStatus();
    }

    @Test
    @DisplayName("login não é isento: 6ª tentativa no minuto do mesmo IP retorna 429 (força bruta)")
    void loginIsRateLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(login("203.0.113.7", null)).isEqualTo(200);
        }
        assertThat(login("203.0.113.7", null)).isEqualTo(429);
    }

    @Test
    @DisplayName("forjar X-Forwarded-For não reseta o limite")
    void spoofedForwardedForDoesNotBypass() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(login("203.0.113.7", "10.0.0." + i)).isEqualTo(200);
        }
        assertThat(login("203.0.113.7", "1.2.3.4")).isEqualTo(429);
    }

    @Test
    @DisplayName("health continua isento")
    void healthIsExempt() throws Exception {
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
            assertThat(res.getStatus()).isEqualTo(200);
        }
    }
}
