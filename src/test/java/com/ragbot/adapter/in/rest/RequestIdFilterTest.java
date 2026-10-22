package com.ragbot.adapter.in.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * TDD #12 — RequestIdFilter.
 * Verifica que toda resposta HTTP carrega um X-Request-ID rastreável.
 */
@WebMvcTest(AuthController.class)
@Import(TestSecurityConfig.class)
@DisplayName("RequestIdFilter")
class RequestIdFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private com.ragbot.application.service.AuthService authService;

    @Test
    @DisplayName("toda resposta deve ter header X-Request-ID")
    void everyResponseShouldHaveRequestIdHeader() throws Exception {
        mockMvc.perform(get("/api/auth/ping"))
                .andExpect(header().exists("X-Request-ID"));
    }

    @Test
    @DisplayName("X-Request-ID deve ser UUID válido")
    void requestIdShouldBeValidUuid() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/health")).andReturn();
        String requestId = result.getResponse().getHeader("X-Request-ID");

        assertThat(requestId)
                .isNotNull()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    @DisplayName("se cliente enviar X-Request-ID, deve ser respeitado")
    void shouldRespectClientProvidedRequestId() throws Exception {
        String clientId = "meu-id-de-rastreamento-123";

        mockMvc.perform(get("/actuator/health")
                        .header("X-Request-ID", clientId))
                .andExpect(header().string("X-Request-ID", clientId));
    }

    @Test
    @DisplayName("duas requisições diferentes devem ter IDs diferentes")
    void differentRequestsShouldHaveDifferentIds() throws Exception {
        MvcResult r1 = mockMvc.perform(get("/actuator/health")).andReturn();
        MvcResult r2 = mockMvc.perform(get("/actuator/health")).andReturn();

        String id1 = r1.getResponse().getHeader("X-Request-ID");
        String id2 = r2.getResponse().getHeader("X-Request-ID");

        assertThat(id1).isNotEqualTo(id2);
    }
}
