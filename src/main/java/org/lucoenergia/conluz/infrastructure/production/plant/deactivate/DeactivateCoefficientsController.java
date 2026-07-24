package org.lucoenergia.conluz.infrastructure.production.plant.deactivate;

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
        value = "/api/v1/plants/{plantId}/sharing-agreements/{id}/partition-coefficients/deactivate",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Validated
public class DeactivateCoefficientsController {

    private final CoefficientActivationService service;

    public DeactivateCoefficientsController(CoefficientActivationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "Reverts a batch of coefficients to pending",
            description = """
                    Retracts the activation of each given coefficient: the distributor never applied
                    it, so no date is correct. Splices the chain -- the coefficient's predecessor for
                    the same (plant, supply), if any, has its `validTo` set to the coefficient's own
                    former `validTo` (which may itself be null, reopening the predecessor to infinity).
                    If there is no predecessor and a successor exists, this deliberately leaves a gap:
                    that supply resolves to zero over the window, the honest representation of "this
                    agreement was never applied".

                    **Required: Community Admin**

                    PUBLISHED or SUPERSEDED agreements only -- see the `activate` endpoint for why DRAFT
                    is rejected and why this is nonetheless the correct write on a non-DRAFT agreement.

                    Idempotent in effect: re-sending a batch that would produce no change is a no-op.

                    Returns 404 if the plant or the agreement does not exist, does not belong to this
                    plant, or the caller is not a member of its community, to avoid leaking existence.
                    Returns 409 if the agreement is DRAFT, or if any coefficient does not belong to it.

                    Authentication is required using a Bearer token.
                    """,
            tags = ApiTag.SHARING_AGREEMENTS,
            operationId = "deactivatePartitionCoefficients",
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
                    description = "The agreement is DRAFT, or one or more coefficients do not belong to it.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = RestError.class))
            )
    })
    @BadRequestErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageSharingAgreement(#plantId, #id)")
    public CoefficientActivationResponse deactivateCoefficients(
            @PathVariable UUID plantId,
            @PathVariable UUID id,
            @Valid @RequestBody DeactivateCoefficientsBody body) {
        List<SupplyPartitionCoefficient> touched = service.setValidFrom(plantId, id, null, body.getCoefficientIds());
        return new CoefficientActivationResponse(touched);
    }
}
