package org.lucoenergia.conluz.infrastructure.price.omie.get;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.NotImplementedException;
import org.lucoenergia.conluz.domain.price.get.GetPriceRepository;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.ConluzRestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.zone.ZoneRules;
import java.util.ArrayList;
import java.util.List;

@Repository
@Qualifier(value = "getPriceRepositoryRest")
public class GetPriceRepositoryRest implements GetPriceRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetPriceRepositoryRest.class);
    private static final String OMIE_URL = "https://www.omie.es/es/file-download";

    private final ConluzRestClientBuilder conluzRestClientBuilder;
    private final TimeConfiguration timeConfiguration;

    public GetPriceRepositoryRest(ConluzRestClientBuilder conluzRestClientBuilder, TimeConfiguration timeConfiguration) {
        this.conluzRestClientBuilder = conluzRestClientBuilder;
        this.timeConfiguration = timeConfiguration;
    }

    @Override
    public List<PriceByHour> getPricesByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate) {
        throw new NotImplementedException();
    }

    @Override
    public List<PriceByHour> getPricesByDay(OffsetDateTime day) {

        final String dayFormatted = formatDay(day);

        final OkHttpClient client = conluzRestClientBuilder.build();

        LOGGER.info("Synchronizing OMIE prices for day {}.", dayFormatted);

        final List<PriceByHour> prices = getPrices(client, "1", dayFormatted);
        if (!prices.isEmpty()) {
            return prices;
        }

        // If the result using number 1 is empty, we try with 2
        return getPrices(client, "2", dayFormatted);
    }

    private String formatDay(OffsetDateTime day) {
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        return day.format(formatter);
    }

    private List<PriceByHour> getPrices(OkHttpClient client, String number, String dayFormatted) {

        final List<PriceByHour> prices = new ArrayList<>();

        final String queryParameter = String.format("?parents[0]=marginalpdbc&filename=marginalpdbc_%s.%s",
                dayFormatted, number);

        final String url = UriComponentsBuilder.fromUriString(OMIE_URL + queryParameter)
                .build()
                .toUriString();

        final Request requestRetry = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = client.newCall(requestRetry).execute()) {

            if (response.isSuccessful()) {

                final ResponseBody responseBody = response.body();
                if (responseBody != null) {
                    final String bodyString = responseBody.string();
                    if (!bodyString.isEmpty()) {
                        return parseBody(bodyString);
                    }
                }
            } else {
                LOGGER.error("Unable to synchronize OMIE prices for day {} using number {}. Code {}, message: {}",
                        dayFormatted, number, response.code(),
                        response.body() != null ? response.body().string() : response.message());
            }
        } catch (IOException e) {
            LOGGER.error(String.format("Unable to sync OMIE prices for day %s", dayFormatted), e);
        }

        return prices;
    }

    /**
     * The response body is a structured text where each line is ;-separated, representing one record:
     * - Code identifier (like MARGINALPDBC;).
     * - Year.
     * - Month.
     * - Day.
     * - Hour.
     * - Two double values.
     */
    private List<PriceByHour> parseBody(String body) {
        final List<PriceByHour> resultList = new ArrayList<>();
        final String[] lines = body.split("\n");

        for (String line : lines) {
            if (!line.trim().isEmpty() && !line.startsWith("*") && !line.startsWith("MARGINALPDBC")) {
                final String[] fields = line.split(";");

                int year = Integer.parseInt(fields[0]);
                int month = Integer.parseInt(fields[1]);
                int day = Integer.parseInt(fields[2]);
                // We substract one hour because the format comes with hourly values from 1 to 24
                int hour = Integer.parseInt(fields[3]) - 1;

                final LocalDateTime dateTime = LocalDateTime.of(year, month, day, hour, 0);

                // Convert LocalDateTime to OffsetDateTime
                final OffsetDateTime offsetDateTime = convertToOffsetDateTime(dateTime, timeConfiguration.getZoneId());

                // We divide the value by 1000 because the price comes in MWh and we use kWh
                double priceKwh = Double.parseDouble(fields[4]) / 1000;
                // Format number to have exactly 6 decimals
                priceKwh = Math.floor(priceKwh * 1e6) / 1e6;

                resultList.add(new PriceByHour(priceKwh, offsetDateTime));
            }
        }
        return resultList;
    }

    public OffsetDateTime convertToOffsetDateTime(LocalDateTime localDateTime, ZoneId zoneId) {
        // Get the ZoneOffset at the specific date/time.
        final ZoneRules rules = zoneId.getRules();
        final ZoneOffset offset = rules.getOffset(localDateTime);

        // Attach the ZoneOffset to the LocalDateTime.
        return localDateTime.atOffset(offset);
    }
}
