package org.lucoenergia.conluz.infrastructure.production;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.security.BasicAuthHeaderGenerator;
import org.lucoenergia.conluz.infrastructure.shared.security.MockUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class GetHourlyProductionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = MockUser.USERNAME, authorities = {MockUser.ROLE})
    void testGetHourlyProduction() throws Exception {
        String authHeader = BasicAuthHeaderGenerator.generate(MockUser.USERNAME, MockUser.PASSWORD);

        mockMvc.perform(get("/api/v1/production/hourly")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", "2023-10-25T00:00:00.000+02:00")
                        .queryParam("endDate", "2023-10-25T23:00:00.000+02:00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("power")));
    }
}
