package org.lucoenergia.conluz.infrastructure.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.lucoenergia.conluz.infrastructure.shared.DefaultAdminUserTestConfiguration.DEFAULT_ADMIN_ID;
import static org.lucoenergia.conluz.infrastructure.shared.DefaultAdminUserTestConfiguration.DEFAULT_ADMIN_PASSWORD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
public class BaseControllerTest extends BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;

    private String defaultAdminToken;

    protected String loginAsDefaultAdmin() throws Exception {

        // Login to get the JWT token
        String loginBody = "{\"username\": \"" + DEFAULT_ADMIN_ID + "\",\"password\": \"" + DEFAULT_ADMIN_PASSWORD + "\"}";

        MvcResult loginResult = mockMvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();

        String loginResponse = loginResult.getResponse().getContentAsString();
        // Parse JSON string into JsonNode
        String token = objectMapper.readTree(loginResponse).get("token").asText();

        return "Bearer " + token;
    }
}
