package org.lucoenergia.conluz.domain.production.datadis.get;

import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.production.datadis.DatadisProduction;
import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for retrieving Datadis-derived production data of a community.
 *
 * <p>Unlike Datadis <em>consumption</em> reads (which are per-supply personal data), production is a
 * communal asset shared by the whole community, so reads are scoped to a community and, optionally,
 * to a single supply within it. Because authorization is community-level (a member may read any
 * plant of their community, not just supplies they own), the per-supply ownership boundary is
 * enforced here in the data layer: every query is constrained to the community's plant CUPS, and a
 * {@code supplyId} that does not back a plant of the community is rejected with
 * {@code SupplyNotFoundException} (mapped to 404) so cross-community reads cannot leak.
 */
public interface GetDatadisProductionService {

    /**
     * Retrieves hourly production data for a community within a date range.
     *
     * @param communityId the community whose production is retrieved
     * @param supplyId    optional supply to narrow the query to a single plant CUPS; when null, all
     *                    plant CUPS of the community are included. Must back a plant of the community.
     * @param startDate   the start date of the range, inclusive
     * @param endDate     the end date of the range, inclusive
     */
    List<DatadisProduction> getHourlyProduction(@NotNull UUID communityId, SupplyId supplyId,
                                                @NotNull OffsetDateTime startDate, @NotNull OffsetDateTime endDate);

    /**
     * Retrieves daily production data for a community within a date range.
     *
     * @see #getHourlyProduction(UUID, SupplyId, OffsetDateTime, OffsetDateTime)
     */
    List<DatadisProduction> getDailyProduction(@NotNull UUID communityId, SupplyId supplyId,
                                               @NotNull OffsetDateTime startDate, @NotNull OffsetDateTime endDate);

    /**
     * Retrieves monthly production data for a community within a date range.
     *
     * @see #getHourlyProduction(UUID, SupplyId, OffsetDateTime, OffsetDateTime)
     */
    List<DatadisProduction> getMonthlyProduction(@NotNull UUID communityId, SupplyId supplyId,
                                                 @NotNull OffsetDateTime startDate, @NotNull OffsetDateTime endDate);

    /**
     * Retrieves yearly production data for a community within a date range.
     *
     * @see #getHourlyProduction(UUID, SupplyId, OffsetDateTime, OffsetDateTime)
     */
    List<DatadisProduction> getYearlyProduction(@NotNull UUID communityId, SupplyId supplyId,
                                                @NotNull OffsetDateTime startDate, @NotNull OffsetDateTime endDate);
}
