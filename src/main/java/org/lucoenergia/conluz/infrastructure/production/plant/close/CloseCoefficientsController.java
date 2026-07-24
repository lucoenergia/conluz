package org.lucoenergia.conluz.infrastructure.production.plant.close;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.activation.CoefficientActivationService;
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.activation.CoefficientActivationResponse;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/plants/{plantId}/sharing-agreements/{id}/partition-coefficients/close",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Validated
public class CloseCoefficientsController {

    private final CoefficientActivationService service;

    public CloseCoefficientsController(CoefficientActivationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "Records or corrects when a batch of coefficients' coverage explicitly ends",
            description = """
                    Sets `validTo` for each given coefficient to `closedOn` -- the exit case: the
                    supply is present in this agreement but absent from its successor, so no
                    activation cascade will ever close it. Also corrects an already-closed date, as
                    long as no successor coefficient starts at or before the requested boundary (that
                    would mean the current close is cascade-derived, not authored, or that the new
                    date would overlap a later row).

                    **Required: Community Admin**

                    PUBLISHED or SUPERSEDED agreements only. The coefficient must already be active
                    (`validFrom` set); closing a pending coefficient is rejected.

                    Idempotent in effect: re-sending a batch that would produce no change is a no-op.

                    Returns 404 if the plant or the agreement does not exist, does not belong to this
                    plant, or the caller is not a member of its community, to avoid leaking existence.
                    Returns 409 if the agreement is DRAFT, or if any coefficient fails validation.

                    Authentication is required using a Bearer token.
                    """,
            tags = ApiTag.SHARING_AGREEMENTS,
            operationId = "closePartitionCoefficients",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The batch has been applied (or was already a no-op).",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The agreement is DRAFT, or one or more coefficients failed validation.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = RestError.class))
            )
    })
    @BadRequestErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageSharingAgreement(#plantId, #id)")
    public CoefficientActivationResponse closeCoefficients(
            @PathVariable UUID plantId,
            @PathVariable UUID id,
            @Valid @RequestBody CloseCoefficientsBody body) {
        List<SupplyPartitionCoefficient> touched = service.setValidTo(plantId, id, body.getClosedOn(), body.getCoefficientIds());
        return new CoefficientActivationResponse(touched);
    }
}
