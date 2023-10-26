package org.lucoenergia.conluz.price;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.shared.security.BasicAuthHeaderGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class GetPriceByHourControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "user", authorities = {"ROLE_USER"})
    public void testGetPriceByRangeOfDates() throws Exception {

        String authHeader = BasicAuthHeaderGenerator.generate("user", "password");

        String expectedContent = "[{\"price\":114.1,\"hour\":\"2023-10-25T00:00:00+02:00\"},{\"price\":98.37,\"hour\":\"2023-10-25T01:00:00+02:00\"},{\"price\":70.75,\"hour\":\"2023-10-25T02:00:00+02:00\"},{\"price\":64.75,\"hour\":\"2023-10-25T03:00:00+02:00\"},{\"price\":62.65,\"hour\":\"2023-10-25T04:00:00+02:00\"},{\"price\":61.42,\"hour\":\"2023-10-25T05:00:00+02:00\"},{\"price\":45.59,\"hour\":\"2023-10-25T06:00:00+02:00\"},{\"price\":59.75,\"hour\":\"2023-10-25T07:00:00+02:00\"},{\"price\":65.1,\"hour\":\"2023-10-25T08:00:00+02:00\"},{\"price\":88.59,\"hour\":\"2023-10-25T09:00:00+02:00\"},{\"price\":104.25,\"hour\":\"2023-10-25T10:00:00+02:00\"},{\"price\":95.0,\"hour\":\"2023-10-25T11:00:00+02:00\"},{\"price\":70.75,\"hour\":\"2023-10-25T12:00:00+02:00\"},{\"price\":62.65,\"hour\":\"2023-10-25T13:00:00+02:00\"},{\"price\":45.48,\"hour\":\"2023-10-25T14:00:00+02:00\"},{\"price\":35.0,\"hour\":\"2023-10-25T15:00:00+02:00\"},{\"price\":20.0,\"hour\":\"2023-10-25T16:00:00+02:00\"},{\"price\":17.45,\"hour\":\"2023-10-25T17:00:00+02:00\"},{\"price\":37.77,\"hour\":\"2023-10-25T18:00:00+02:00\"},{\"price\":64.75,\"hour\":\"2023-10-25T19:00:00+02:00\"},{\"price\":85.0,\"hour\":\"2023-10-25T20:00:00+02:00\"},{\"price\":97.56,\"hour\":\"2023-10-25T21:00:00+02:00\"},{\"price\":109.68,\"hour\":\"2023-10-25T22:00:00+02:00\"},{\"price\":105.97,\"hour\":\"2023-10-25T23:00:00+02:00\"}]";

        mockMvc.perform(get("/api/v1/price")
                        .header("Authorization", authHeader)
                        .queryParam("startDate", "2023-10-25T00:00:00.000+02:00")
                        .queryParam("endDate", "2023-10-25T23:00:00.000+02:00"))
                .andExpect(status().isOk())
                .andExpect(content().string(expectedContent));
    }
}
