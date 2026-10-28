package org.lucoenergia.conluz.infrastructure.price.sync;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.price.sync.SyncDailyPricesService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/prices/sync")
public class SyncPricesController {

    private final SyncDailyPricesService syncDailyPricesService;

    public SyncPricesController(SyncDailyPricesService syncDailyPricesService) {
        this.syncDailyPricesService = syncDailyPricesService;
    }

    @PostMapping
    @Operation(
            summary = "Re-synchronise the OMIE prices of a range of market days.",
            description = """
                    Fetches the OMIE day-ahead prices of each market day of the range again, the same way the daily \
                    job does, and stores them: each day's prices replace those stored at the same instants. A day \
                    OMIE does not serve keeps whatever is stored for it. Sending the same request again, for \
                    example after one that was interrupted, leaves the stored prices as a single successful run \
                    would.

                    **Range bounds:** the range is half-open, `[startDate, endDate)`: `startDate` is included and \
                    `endDate` is not, so `startDate=2026-03-28` and `endDate=2026-03-30` synchronise 28 and 29 \
                    March. Both are market days, calendar days in Europe/Madrid.

                    **Range checks:** a range is checked in this order, and the first rule it breaks is answered \
                    400 with that rule's code, before anything is fetched:

                    1. `startDate` is strictly before `endDate`: equal dates make an empty range \
                    (`PRICE_SYNC_PERIOD_START_AFTER_END`).
                    2. The range is at most 31 days long (`PRICE_SYNC_PERIOD_TOO_LONG`).
                    3. The range extends no further than tomorrow in Europe/Madrid: `endDate` is no later than the \
                    day after tomorrow (`PRICE_SYNC_PERIOD_END_BEYOND_TOMORROW`). A range beyond tomorrow is \
                    rejected, not clipped.

                    The response lists every day of the range in date order, stored or skipped, and is 200 even \
                    when some days are skipped. OMIE publishes tomorrow's prices around 13:00 Europe/Madrid, so \
                    before then tomorrow is skipped with `FILE_NOT_FOUND`. Each day is one request to OMIE, which \
                    can take up to 30 seconds, so a long range takes minutes.

                    **Required: an enabled platform admin.** Any other authenticated caller gets 403.
                    """,
            tags = ApiTag.PRICES,
            operationId = "syncPrices",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Each day of the range, in date order, stored or skipped",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canSyncPrices()")
    public List<PriceSyncResponse> syncPrices(@Valid @RequestBody SyncPricesBody body) {
        return syncDailyPricesService.syncDailyPricesByDays(body.getStartDate(), body.getEndDate()).stream()
                .map(PriceSyncResponse::new)
                .toList();
    }
}
