package org.lucoenergia.conluz.infrastructure.price;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.EnergyPricesInfluxLoader;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.MockInfluxDbConfiguration;
import org.lucoenergia.conluz.infrastructure.shared.security.BasicAuthHeaderGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GetPriceByHourControllerTest extends BaseControllerTest {

    @Autowired
    private EnergyPricesInfluxLoader energyPricesInfluxLoader;

    @BeforeEach
    void beforeEach() {
        energyPricesInfluxLoader.loadData(MockInfluxDbConfiguration.INFLUX_DB_NAME);
    }

    @BeforeEach
    void afterEach() {
        energyPricesInfluxLoader.clearData();
    }

    @Test
    void testGetPriceByRangeOfDates() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        String expectedContent = "[{\"price\":114.1,\"hour\":\"2023-10-25T00:00:00+02:00\"},{\"price\":98.37,\"hour\":\"2023-10-25T01:00:00+02:00\"},{\"price\":70.75,\"hour\":\"2023-10-25T02:00:00+02:00\"},{\"price\":64.75,\"hour\":\"2023-10-25T03:00:00+02:00\"},{\"price\":62.65,\"hour\":\"2023-10-25T04:00:00+02:00\"},{\"price\":61.42,\"hour\":\"2023-10-25T05:00:00+02:00\"},{\"price\":45.59,\"hour\":\"2023-10-25T06:00:00+02:00\"},{\"price\":59.75,\"hour\":\"2023-10-25T07:00:00+02:00\"},{\"price\":65.1,\"hour\":\"2023-10-25T08:00:00+02:00\"},{\"price\":88.59,\"hour\":\"2023-10-25T09:00:00+02:00\"},{\"price\":104.25,\"hour\":\"2023-10-25T10:00:00+02:00\"},{\"price\":95.0,\"hour\":\"2023-10-25T11:00:00+02:00\"},{\"price\":70.75,\"hour\":\"2023-10-25T12:00:00+02:00\"},{\"price\":62.65,\"hour\":\"2023-10-25T13:00:00+02:00\"},{\"price\":45.48,\"hour\":\"2023-10-25T14:00:00+02:00\"},{\"price\":35.0,\"hour\":\"2023-10-25T15:00:00+02:00\"},{\"price\":20.0,\"hour\":\"2023-10-25T16:00:00+02:00\"},{\"price\":17.45,\"hour\":\"2023-10-25T17:00:00+02:00\"},{\"price\":37.77,\"hour\":\"2023-10-25T18:00:00+02:00\"},{\"price\":64.75,\"hour\":\"2023-10-25T19:00:00+02:00\"},{\"price\":85.0,\"hour\":\"2023-10-25T20:00:00+02:00\"},{\"price\":97.56,\"hour\":\"2023-10-25T21:00:00+02:00\"},{\"price\":109.68,\"hour\":\"2023-10-25T22:00:00+02:00\"},{\"price\":105.97,\"hour\":\"2023-10-25T23:00:00+02:00\"}]";

        mockMvc.perform(get("/api/v1/prices")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", "2023-10-25T00:00:00.000+02:00")
                        .queryParam("endDate", "2023-10-25T23:00:00.000+02:00"))
                .andExpect(status().isOk())
                .andExpect(content().string(expectedContent));
    }

    @Test
    void testInvalidDateFormat() throws Exception {

        String invalidStartDate = "foo";
        String validEndDate = "2023-10-25T23:00:00.000+02:00";

        String authHeader = loginAsDefaultAdmin();


        mockMvc.perform(get("/api/v1/prices")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", invalidStartDate)
                        .queryParam("endDate", validEndDate))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("\"traceId\":")))
                .andExpect(content().string(containsString("\"timestamp\":")))
                .andExpect(content().string(containsString("\"status\":400")))
                .andExpect(content().string(containsString("\"message\":\"El campo con nombre 'startDate' y valor 'foo' tiene un formato incorrecto. El formato esperado es 'yyyy-mm-ddThh:mm:ss.000+h:mm'\"")));
    }
}
