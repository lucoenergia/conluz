package org.lucoenergia.conluz.infrastructure.production.sharingagreement.update;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.update.UpdateSharingAgreementService;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantService;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.SharingAgreementCapabilitiesAssembler;

@RestController
@RequestMapping(
        value = "/api/v1/plants/{plantId}/sharing-agreements/{sharingAgreementId}",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Validated
public class UpdateSharingAgreementController {

    private final UpdateSharingAgreementService service;
    private final GetPlantService plantService;
    private final SharingAgreementCapabilitiesAssembler capabilitiesAssembler;

    public UpdateSharingAgreementController(UpdateSharingAgreementService service, GetPlantService plantService,
                                    SharingAgreementCapabilitiesAssembler capabilitiesAssembler) {
        this.service = service;
        this.plantService = plantService;
        this.capabilitiesAssembler = capabilitiesAssembler;
    }

    @PutMapping
    @Operation(
            summary = "Replaces a sharing agreement's name, notes and installed power",
            description = """
                    This endpoint replaces the name, notes and installed power of a sharing agreement,
                    regardless of its status: DRAFT, PUBLISHED and SUPERSEDED agreements can all be
                    edited this way. All three fields must be provided; this is a full replacement of
                    the updatable fields, not a partial update. Its status, plant and creation metadata
                    can never be changed through this endpoint. The acting user and the time of the
                    edit are recorded as updatedBy/updatedAt on the returned agreement.

                    **Required: Community Admin**

                    Returns 404 if the plant or the agreement does not exist, does not belong to this
                    plant, or the caller is not a member of its community, to avoid leaking existence.

                    Authentication is required using a Bearer token.
                    """,
            tags = ApiTag.SHARING_AGREEMENTS,
            operationId = "updateSharingAgreement",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The sharing agreement has been successfully updated.",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageSharingAgreement(#plantId, #sharingAgreementId)")
    public SharingAgreementResponse updateSharingAgreement(@AuthenticationPrincipal User currentUser,
                                                            @PathVariable UUID plantId, @PathVariable UUID sharingAgreementId,
                                                            @Valid @RequestBody UpdateSharingAgreementBody body) {
        SharingAgreement agreement = service.update(plantId, sharingAgreementId,
                body.mapToUpdateSharingAgreement(currentUser.getId()));
        Plant plant = plantService.findById(PlantId.of(plantId));
        return new SharingAgreementResponse(agreement,
                capabilitiesAssembler.assemble(currentUser, plant, agreement));
    }
}
