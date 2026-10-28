package org.lucoenergia.conluz.infrastructure.price.get;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.domain.price.get.GetPriceRepository;
import org.lucoenergia.conluz.infrastructure.price.OmieConfig;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.ConluzRestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Repository
@Qualifier(value = "getPriceRepositoryRest")
public class GetPriceRepositoryRest implements GetPriceRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetPriceRepositoryRest.class);
    private static final String OMIE_URL = "https://www.omie.es/es/file-download";

    private static final long QUARTER_MINUTES = 15;
    private static final long HOUR_MINUTES = 60;

    private final ConluzRestClientBuilder conluzRestClientBuilder;

    public GetPriceRepositoryRest(ConluzRestClientBuilder conluzRestClientBuilder) {
        this.conluzRestClientBuilder = conluzRestClientBuilder;
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

            try {
                List<PriceByHour> pricesPerDay = getPrices(client, "1", dayFormatted);
                if (pricesPerDay.isEmpty()) {
                    // If the result using number 1 is empty, we try with 2
                    pricesPerDay = getPrices(client, "2", dayFormatted);
                }
                prices.addAll(pricesPerDay);
                LOGGER.debug("Prices for day {}: {}", currentDate, pricesPerDay);
            } catch (OmiePriceFileException e) {
                // A day that cannot be parsed is skipped, so it does not prevent the other days from being stored
                LOGGER.error(String.format("Unable to parse OMIE prices for day %s", dayFormatted), e);
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
     * - Year.
     * - Month.
     * - Day.
     * - Period, numbered from 1.
     * - Two double values.
     * The first line is a header (MARGINALPDBC;) and the last one a trailer (*).
     * <p>
     * A period lasts 15 or 60 minutes. Period n starts (n - 1) periods after the start of the market day in
     * {@link OmieConfig#OMIE_ZONE}, so a day lasting 23 or 25 hours has fewer or more periods than a 24-hour one.
     */
    private List<PriceByHour> parseBody(String body) {
        final List<String[]> rows = Arrays.stream(body.split("\n"))
                .filter(line -> !line.trim().isEmpty() && !line.startsWith("*") && !line.startsWith("MARGINALPDBC"))
                .map(line -> line.split(";"))
                .toList();
        if (rows.isEmpty()) {
            throw new OmiePriceFileException("OMIE response has no price rows");
        }

        final LocalDate day = parseDay(rows.get(0));
        final ZonedDateTime dayStart = day.atStartOfDay(OmieConfig.OMIE_ZONE);
        final long dayMinutes = Duration.between(dayStart, day.plusDays(1).atStartOfDay(OmieConfig.OMIE_ZONE))
                .toMinutes();
        final long periodMinutes = getPeriodMinutes(day, rows.size(), dayMinutes);

        final List<PriceByHour> resultList = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            final String[] fields = rows.get(i);
            final int period = Integer.parseInt(fields[3].trim());
            if (!day.equals(parseDay(fields)) || period != i + 1) {
                throw new OmiePriceFileException(String.format(
                        "OMIE response for day %s has row %d out of sequence", day, i + 1));
            }

            final OffsetDateTime hour = dayStart.toInstant()
                    .plus((period - 1) * periodMinutes, ChronoUnit.MINUTES)
                    .atZone(OmieConfig.OMIE_ZONE)
                    .toOffsetDateTime();

            // We divide the value by 1000 because the price comes in MWh and we use kWh
            double priceKwh = Double.parseDouble(fields[4]) / 1000;
            // Format number to have exactly 6 decimals
            priceKwh = Math.floor(priceKwh * 1e6) / 1e6;

            resultList.add(new PriceByHour(priceKwh, hour));
        }
        return resultList;
    }

    private static long getPeriodMinutes(LocalDate day, int numPeriods, long dayMinutes) {
        if (numPeriods * QUARTER_MINUTES == dayMinutes) {
            return QUARTER_MINUTES;
        }
        if (numPeriods * HOUR_MINUTES == dayMinutes) {
            return HOUR_MINUTES;
        }
        throw new OmiePriceFileException(String.format(
                "OMIE response for day %s has %d periods, which fill neither 15-minute nor hourly periods of a %d-minute day",
                day, numPeriods, dayMinutes));
    }

    private static LocalDate parseDay(String[] fields) {
        return LocalDate.of(Integer.parseInt(fields[0].trim()), Integer.parseInt(fields[1].trim()),
                Integer.parseInt(fields[2].trim()));
    }
}
