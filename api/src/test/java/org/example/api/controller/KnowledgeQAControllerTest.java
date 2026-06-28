package org.example.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.core.service.KnowledgeQAService;
import org.example.model.AgenticAskRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KnowledgeQAController.class)
class KnowledgeQAControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private KnowledgeQAService qaService;

    @Test
    void askAgent_shouldReturn400ForBlankQuestion() throws Exception {
        AgenticAskRequest request = new AgenticAskRequest();
        request.setUserId("user1");
        request.setQuestion("");

        mockMvc.perform(post("/api/qa/ask/agent")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void askAgent_shouldReturn400ForNullQuestion() throws Exception {
        AgenticAskRequest request = new AgenticAskRequest();
        request.setUserId("user1");

        mockMvc.perform(post("/api/qa/ask/agent")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
