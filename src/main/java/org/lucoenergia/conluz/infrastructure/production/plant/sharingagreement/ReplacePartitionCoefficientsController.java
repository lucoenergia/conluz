package org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.MaterializeSharingAgreementCoefficientsService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/plants/{plantId}/sharing-agreements/{id}/partition-coefficients",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Validated
public class ReplacePartitionCoefficientsController {

    private final MaterializeSharingAgreementCoefficientsService service;

    public ReplacePartitionCoefficientsController(MaterializeSharingAgreementCoefficientsService service) {
        this.service = service;
    }

    @PutMapping
    @Operation(
            summary = "Replaces a DRAFT sharing agreement's partition coefficients",
            description = """
                    Atomically replaces the agreement's entire coefficient set with the given one, as
                    pending rows (not yet active). This is the manual-authoring counterpart of uploading
                    a distributor file.

                    **Required: Community Admin**

                    Returns 404 if the plant or the agreement does not exist, does not belong to this
                    plant, or the caller is not a member of its community, to avoid leaking existence.
                    Returns 409 if the agreement is not in DRAFT status.
                    Returns 400 if the same CUPS appears more than once, or any CUPS is not a known
                    supply of the plant's community.

                    Authentication is required using a Bearer token.
                    """,
            tags = ApiTag.SHARING_AGREEMENTS,
            operationId = "replacePartitionCoefficients",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The coefficient set has been replaced.",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The agreement is not in DRAFT status.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = RestError.class))
            )
    })
    @BadRequestErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageSharingAgreement(#plantId, #id)")
    public ReplacePartitionCoefficientsResponse replacePartitionCoefficients(
            @PathVariable UUID plantId,
            @PathVariable UUID id,
            @Valid @RequestBody ReplacePartitionCoefficientsBody body) {
        List<SupplyPartitionCoefficient> saved = service.replaceAll(plantId, id, body.mapToEntries());
        return new ReplacePartitionCoefficientsResponse(saved);
    }
}
