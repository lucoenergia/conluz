package org.lucoenergia.conluz.infrastructure.production.plant.activate;

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
        value = "/api/v1/plants/{plantId}/sharing-agreements/{id}/partition-coefficients/activate",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Validated
public class ActivateCoefficientsController {

    private final CoefficientActivationService service;

    public ActivateCoefficientsController(CoefficientActivationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "Records or corrects the date the distributor applied a batch of coefficients",
            description = """
                    Sets `validFrom` for each of the given coefficients to `appliedOn` (activation), or
                    corrects it if already set. Cascades: each coefficient's previously-open predecessor
                    for the same (plant, supply), if any, has its `validTo` set to `appliedOn` in the
                    same transaction.

                    **Required: Community Admin**

                    PUBLISHED or SUPERSEDED agreements only -- DRAFT is rejected (a DRAFT has not been
                    finalised, and a subsequent coefficient replacement would delete the activated row).
                    This is the one write allowed on a non-DRAFT agreement: it records WHEN the
                    distributor applied a coefficient, not WHICH coefficients exist (sealed at publish).

                    Idempotent in effect: re-sending a batch that would produce no change is a no-op,
                    not an error. A failure on any item rejects the whole batch -- nothing is written.

                    Returns 404 if the plant or the agreement does not exist, does not belong to this
                    plant, or the caller is not a member of its community, to avoid leaking existence.
                    Returns 409 if the agreement is DRAFT, or if any coefficient fails validation (does
                    not belong to this agreement, the date is in the future, or the date is not after
                    the resolved predecessor's own date / not before the coefficient's own end date).

                    Authentication is required using a Bearer token.
                    """,
            tags = ApiTag.SHARING_AGREEMENTS,
            operationId = "activatePartitionCoefficients",
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
    public CoefficientActivationResponse activateCoefficients(
            @PathVariable UUID plantId,
            @PathVariable UUID id,
            @Valid @RequestBody ActivateCoefficientsBody body) {
        List<SupplyPartitionCoefficient> touched = service.setValidFrom(plantId, id, body.getAppliedOn(), body.getCoefficientIds());
        return new CoefficientActivationResponse(touched);
    }
}
