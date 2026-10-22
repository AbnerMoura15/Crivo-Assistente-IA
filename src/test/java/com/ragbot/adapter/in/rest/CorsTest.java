package com.ragbot.adapter.in.rest;

import com.ragbot.application.port.in.QueryChatUseCase;
import com.ragbot.application.service.ChatHistoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@Import(TestWebSecurityConfig.class)
@TestPropertySource(properties = "ragbot.cors.allowed-origins=https://app.exemplo.com")
@DisplayName("CORS restrito")
class CorsTest {

    @Autowired MockMvc mockMvc;

    @MockBean QueryChatUseCase queryChatUseCase;
    @MockBean ChatHistoryService chatHistoryService;

    private org.springframework.test.web.servlet.ResultActions preflight(String origin) throws Exception {
        return mockMvc.perform(options("/api/chat")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type"));
    }

    @Test
    @DisplayName("origem não listada é recusada (sem Access-Control-Allow-Origin)")
    void unknownOriginIsRejected() throws Exception {
        preflight("https://site-malicioso.com")
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("origem configurada recebe o header de CORS")
    void configuredOriginIsAllowed() throws Exception {
        preflight("https://app.exemplo.com")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.exemplo.com"));
    }
}
