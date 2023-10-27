package org.lucoenergia.conluz.infrastructure.production;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.GetInstantProductionService;
import org.lucoenergia.conluz.domain.production.InstantProduction;
import org.lucoenergia.conluz.infrastructure.shared.security.BasicAuthHeaderGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class GetInstantProductionControllerTest {

    @Autowired
    private MockMvc mockMvc;


    @Test
    @WithMockUser(username = "user", authorities = {"ROLE_USER"})
    void testGetInstantProduction() throws Exception {
        String authHeader = BasicAuthHeaderGenerator.generate("user", "password");

        mockMvc.perform(get("/api/v1/production")
                        .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("power")));
    }
}
