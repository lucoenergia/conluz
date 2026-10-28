package org.lucoenergia.conluz.infrastructure.price.get;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.domain.price.sync.GetOmiePricesRepository;
import org.lucoenergia.conluz.domain.price.sync.OmieDayPrices;
import org.lucoenergia.conluz.infrastructure.price.OmieConfig;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.ConluzRestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class GetPriceRepositoryRest implements GetOmiePricesRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetPriceRepositoryRest.class);
    private static final String OMIE_URL = "https://www.omie.es/es/file-download";

    private static final long QUARTER_MINUTES = 15;
    private static final long HOUR_MINUTES = 60;
    private static final int SPAIN_PRICE_COLUMN = 5;

    private final ConluzRestClientBuilder conluzRestClientBuilder;

    public GetPriceRepositoryRest(ConluzRestClientBuilder conluzRestClientBuilder) {
        this.conluzRestClientBuilder = conluzRestClientBuilder;
    }

    @Override
    public List<OmieDayPrices> getPricesByDays(LocalDate startDate, LocalDate endDate) {

        LOGGER.info("Getting OMIE prices from {} to {}, exclusive.", startDate, endDate);

        final List<OmieDayPrices> days = new ArrayList<>();
        final OkHttpClient client = conluzRestClientBuilder.build(false, Duration.ofSeconds(30));

        for (LocalDate day = startDate; day.isBefore(endDate); day = day.plusDays(1)) {
            days.add(getDayPrices(client, day));
        }

        return days;
    }

    private OmieDayPrices getDayPrices(OkHttpClient client, LocalDate day) {

        final String dayFormatted = formatDay(day);
        LOGGER.info("Synchronizing OMIE prices for day {}.", dayFormatted);

        final OmieFile first = getFile(client, "1", dayFormatted);
        if (first.body() != null) {
            return parse(day, dayFormatted, first.body());
        }
        // If there is no file with number 1, we try with 2
        final OmieFile second = getFile(client, "2", dayFormatted);
        if (second.body() != null) {
            return parse(day, dayFormatted, second.body());
        }
        return OmieDayPrices.notServed(day, first.fetchFailed() || second.fetchFailed()
                ? OmieDayPrices.Status.FETCH_FAILED
                : OmieDayPrices.Status.NOT_FOUND);
    }

    /**
     * Any failure to parse a day's file rejects that day alone, so it does not prevent the other days from being
     * stored: not only a format mismatch the parser detects, but also a malformed row it trips over, such as a price
     * that is not a number or a missing column.
     */
    private OmieDayPrices parse(LocalDate day, String dayFormatted, String body) {
        try {
            final List<PriceByHour> prices = parseBody(body);
            LOGGER.debug("Prices for day {}: {}", day, prices);
            return OmieDayPrices.served(day, prices);
        } catch (RuntimeException e) {
            LOGGER.error(String.format("Unable to parse OMIE prices for day %s", dayFormatted), e);
            return OmieDayPrices.notServed(day, OmieDayPrices.Status.REJECTED);
        }
    }

    private String formatDay(LocalDate day) {
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        return day.format(formatter);
    }

    /**
     * The body of one OMIE file, or null when there is none to parse. {@code fetchFailed} tells a file that could not
     * be fetched (a connection failure or a server error) from one that OMIE does not have.
     */
    private record OmieFile(String body, boolean fetchFailed) {
    }

    private OmieFile getFile(OkHttpClient client, String number, String dayFormatted) {

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
                        return new OmieFile(bodyString, false);
                    }
                }
            } else {
                LOGGER.error("Unable to synchronize OMIE prices for day {} using number {}. Code {}, message: {}",
                        dayFormatted, number, response.code(),
                        response.body() != null ? response.body().string() : response.message());
                return new OmieFile(null, response.code() >= 500);
            }
        } catch (IOException e) {
            LOGGER.error(String.format("Unable to sync OMIE prices for day %s", dayFormatted), e);
            return new OmieFile(null, true);
        }

        return new OmieFile(null, false);
    }

    /**
     * The response body is a structured text where each line is ;-separated, representing one record:
     * - Year.
     * - Month.
     * - Day.
     * - Period, numbered from 1.
     * - Marginal price of the Portuguese system, in EUR/MWh.
     * - Marginal price of the Spanish system, in EUR/MWh. This is the price stored.
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
            double priceKwh = Double.parseDouble(fields[SPAIN_PRICE_COLUMN]) / 1000;
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
