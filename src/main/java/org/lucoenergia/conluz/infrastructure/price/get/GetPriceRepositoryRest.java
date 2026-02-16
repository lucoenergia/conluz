package org.lucoenergia.conluz.infrastructure.price.get;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.domain.price.get.GetPriceRepository;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.ConluzRestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.*;
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

        LOGGER.info("Getting OMIE prices for intervale of {} and {}.", startDate, endDate);

        final List<PriceByHour> prices = new ArrayList<>();
        final OkHttpClient client = conluzRestClientBuilder.build(false, Duration.ofSeconds(30));

        OffsetDateTime currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            LOGGER.info("Getting OMIE prices for day {}.", currentDate);

            final String dayFormatted = formatDay(currentDate);
            LOGGER.info("Synchronizing OMIE prices for day {}.", dayFormatted);

            final List<PriceByHour> pricesPerDay = getPrices(client, "1", dayFormatted);
            if (!pricesPerDay.isEmpty()) {
                prices.addAll(pricesPerDay);
                LOGGER.debug("Prices for day {}: {}", currentDate, prices);
            } else {
                // If the result using number 1 is empty, we try with 2
                prices.addAll(getPrices(client, "2", dayFormatted));
                LOGGER.debug("Prices for day {}: {}", currentDate, prices);
            }

            currentDate = currentDate.plusDays(1);
        }

        return prices;
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

        LocalDateTime dateTime = null;

        for (String line : lines) {
            if (!line.trim().isEmpty() && !line.startsWith("*") && !line.startsWith("MARGINALPDBC")) {
                final String[] fields = line.split(";");

                if (lines.length == 26) {
                    dateTime = getDateTimeHourly(fields);
                } else if (lines.length == 98) {
                    dateTime = getDateTimeEvery15Minutes(dateTime, fields);
                } else {
                    LOGGER.error("Unexpected number of lines in OMIE response: {}", lines.length);
                    throw new IllegalArgumentException("Unexpected number of lines in OMIE response");
                }

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

    private static LocalDateTime getDateTimeEvery15Minutes(LocalDateTime dateTime, String[] fields) {
        if (dateTime == null) {
            final int year = Integer.parseInt(fields[0]);
            final int month = Integer.parseInt(fields[1]);
            final int day = Integer.parseInt(fields[2]);
            dateTime = LocalDateTime.of(year, month, day, 0, 0);
        } else {
            dateTime = dateTime.plusMinutes(15);
        }
        return dateTime;
    }

    private static LocalDateTime getDateTimeHourly(String[] fields) {
        LocalDateTime dateTime;
        int year = Integer.parseInt(fields[0]);
        int month = Integer.parseInt(fields[1]);
        int day = Integer.parseInt(fields[2]);
        // We substract one hour because the format comes with hourly values from 1 to 24
        int hour = Integer.parseInt(fields[3]) - 1;

        dateTime = LocalDateTime.of(year, month, day, hour, 0);
        return dateTime;
    }

    public OffsetDateTime convertToOffsetDateTime(LocalDateTime localDateTime, ZoneId zoneId) {
        // Get the ZoneOffset at the specific date/time.
        final ZoneRules rules = zoneId.getRules();
        final ZoneOffset offset = rules.getOffset(localDateTime);

        // Attach the ZoneOffset to the LocalDateTime.
        return localDateTime.atOffset(offset);
    }
}
