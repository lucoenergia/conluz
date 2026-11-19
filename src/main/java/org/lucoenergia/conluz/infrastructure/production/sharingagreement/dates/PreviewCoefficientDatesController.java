package org.lucoenergia.conluz.infrastructure.production.sharingagreement.dates;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.PreviewCoefficientDatesService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/plants/{plantId}/sharing-agreements/{sharingAgreementId}/partition-coefficients/preview-dates",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Validated
public class PreviewCoefficientDatesController {

    private final PreviewCoefficientDatesService service;

    public PreviewCoefficientDatesController(PreviewCoefficientDatesService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "Previews every effect of setting the dates of an agreement's coefficients and leaving closures",
            description = """
                    Computes what `set-dates` would do with the same body, and writes nothing. The effects are those
                    `set-dates` performs on the coefficients as they stand now; `set-dates` validates the request
                    again when it runs, since the coefficients may have changed in between.

                    Each coefficient is placed chronologically within the timeline of its (plant, supply), as
                    `activate` does, in ascending `validFrom` order and then by coefficient id, each on the timeline
                    as the previous ones left it. A pending coefficient is activated and an active one corrected.

                    Each listed leaving supply must have no coefficient, pending or not, in this agreement. Its
                    closed coefficient is its activated coefficient with the latest `validFrom` before the
                    agreement's earliest `validFrom` once the coefficients of the request are placed. Its `validTo`
                    becomes `closedOn`: that may shorten it or extend it, up to the next start of the supply. It is
                    an ordinary close, which `reopen` undoes.

                    On the agreement's first activation (none of its coefficients had a `validFrom` before the
                    request), every supply that leaves the distribution and is not listed is closed at the earliest
                    `validFrom` of the request, as `activate` does, and reported with source `DEFAULT`. A later
                    request closes only the listed supplies.

                    The response lists every effect: each requested coefficient's period before and after, each
                    neighbour whose end moves, each leaving closure, the status of every agreement owning a changed
                    coefficient before and after, and the earliest `validFrom` of the plant's community before and
                    after. Rows that do not change are not listed. A row error in `details` carries `coefficientId`,
                    `supplyId` or both, and `conflictingCoefficientId` for an overlap.

                    **Required: Community Admin**

                    PUBLISHED or SUPERSEDED agreements only.

                    Returns 400 if both lists are empty or a coefficient or a supply is listed twice.
                    Returns 404 if the plant or the agreement does not exist, does not belong to this plant, or the
                    caller is not a member of its community, to avoid leaking existence. Returns 409 if the agreement
                    is DRAFT, or with every invalid row: a coefficient not in this agreement, a date in the future,
                    a start equal to another start of the supply or a period overlapping another, a correction not
                    before the coefficient's own close, a listed supply holding a coefficient in this agreement, a
                    listed supply with no coefficient to close, or a closing date not after the closed coefficient's
                    start.

                    Authentication is required using a Bearer token.
                    """,
            tags = ApiTag.SHARING_AGREEMENTS,
            operationId = "previewPartitionCoefficientDates",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The request is valid; the effects it would have. Nothing is written.",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The agreement is DRAFT, or one or more rows failed validation.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = RestError.class))
            )
    })
    @BadRequestErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageSharingAgreement(#plantId, #sharingAgreementId)")
    public CoefficientDatesEffectsResponse previewCoefficientDates(
            @PathVariable UUID plantId,
            @PathVariable UUID sharingAgreementId,
            @Valid @RequestBody SetCoefficientDatesBody body) {
        return new CoefficientDatesEffectsResponse(service.previewDates(plantId, sharingAgreementId, body.toRequest()));
    }
}
