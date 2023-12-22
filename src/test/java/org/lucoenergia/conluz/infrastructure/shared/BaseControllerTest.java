package org.lucoenergia.conluz.infrastructure.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.lucoenergia.conluz.infrastructure.admin.config.InitBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.lucoenergia.conluz.domain.admin.user.DefaultUserAdminMother.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
public class BaseControllerTest extends BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;

    protected String loginAsDefaultAdmin() throws Exception {

        // Initialize default admin user
        init();

        // Login to get the JWT token
        String loginBody = "{\"username\": \"" + PERSONAL_ID + "\",\"password\": \"" + PASSWORD + "\"}";

        MvcResult loginResult = mockMvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        String loginResponse = loginResult.getResponse().getContentAsString();
        // Parse JSON string into JsonNode
        String token = objectMapper.readTree(loginResponse).get("token").asText();

        return "Bearer " + token;
    }

    protected MvcResult init() throws Exception {

        InitBody body = new InitBody();
        InitBody.CreateDefaultAdminUserBody createDefaultAdminUserBody = new InitBody.CreateDefaultAdminUserBody();
        createDefaultAdminUserBody.setPersonalId(PERSONAL_ID);
        createDefaultAdminUserBody.setPassword(PASSWORD);
        createDefaultAdminUserBody.setFullName(FULL_NAME);
        createDefaultAdminUserBody.setAddress(ADDRESS);
        createDefaultAdminUserBody.setEmail(EMAIL);
        body.setDefaultAdminUser(createDefaultAdminUserBody);

        String bodyAsString = objectMapper.writeValueAsString(body);

        return mockMvc.perform(post("/api/v1/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAsString))
                .andDo(print())
                .andReturn();
    }
}
