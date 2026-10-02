package org.lucoenergia.conluz.infrastructure.production.sharingagreement.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementService;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantService;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.SharingAgreementCapabilitiesAssembler;

@RestController
@RequestMapping(value = "/api/v1/plants/{plantId}/sharing-agreements", produces = MediaType.APPLICATION_JSON_VALUE)
public class GetSharingAgreementsController {

    private final GetSharingAgreementService service;
    private final GetPlantService plantService;
    private final SharingAgreementCapabilitiesAssembler capabilitiesAssembler;

    public GetSharingAgreementsController(GetSharingAgreementService service, GetPlantService plantService,
                                    SharingAgreementCapabilitiesAssembler capabilitiesAssembler) {
        this.service = service;
        this.plantService = plantService;
        this.capabilitiesAssembler = capabilitiesAssembler;
    }

    @GetMapping
    @Operation(
            summary = "Lists the sharing agreements of a plant",
            description = """
                    Returns the sharing agreements of the given plant, newest first, optionally filtered by status.

                    **Required: community admin of the plant's community.**

                    Returns 404 if the plant does not exist or if the caller is not a member of its
                    community, to avoid leaking the existence of plants by ID. Returns 403 if the
                    caller is an enabled member of the plant's community but not a community admin.

                    Authentication is required using a Bearer token.
                    """,
            tags = ApiTag.SHARING_AGREEMENTS,
            operationId = "getSharingAgreements",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Sharing agreements retrieved successfully",
                    useReturnTypeSchema = true
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canListSharingAgreements(#plantId)")
    public List<SharingAgreementResponse> getSharingAgreements(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID plantId,
            @Parameter(description = "Filter by status") @RequestParam(required = false) SharingAgreementStatus status) {
        List<SharingAgreement> sharingAgreements = service.findByPlantId(plantId, status);
        // Loaded once for the whole page: the agreements carry only a plantId, and the rule needs
        // the plant itself to decide whether the caller may see it.
        Plant plant = plantService.findById(PlantId.of(plantId));
        return sharingAgreements.stream()
                .map(agreement -> new SharingAgreementResponse(agreement,
                        capabilitiesAssembler.assemble(currentUser, plant, agreement)))
                .toList();
    }
}
