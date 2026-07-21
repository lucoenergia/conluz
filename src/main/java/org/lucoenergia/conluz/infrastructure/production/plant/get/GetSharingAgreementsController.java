package org.lucoenergia.conluz.infrastructure.production.plant.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.production.plant.SharingAgreement;
import org.lucoenergia.conluz.domain.production.plant.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.plant.get.GetSharingAgreementService;
import org.lucoenergia.conluz.infrastructure.production.plant.SharingAgreementResponse;
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

@RestController
@RequestMapping(value = "/api/v1/plants/{plantId}/sharing-agreements", produces = MediaType.APPLICATION_JSON_VALUE)
public class GetSharingAgreementsController {

    private final GetSharingAgreementService service;

    public GetSharingAgreementsController(GetSharingAgreementService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Lists the sharing agreements of a plant",
            description = """
                    Returns the sharing agreements of the given plant, newest first, optionally filtered by status.

                    **Required: any member of the plant's community (any role).**

                    Returns 404 if the plant does not exist OR if the caller is not a member of its
                    community, to avoid leaking the existence of plants by ID.

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
    @PreAuthorize("@communityAccessGuard.canReadPlant(#plantId)")
    public List<SharingAgreementResponse> getSharingAgreements(
            @PathVariable UUID plantId,
            @Parameter(description = "Filter by status") @RequestParam(required = false) SharingAgreementStatus status) {
        List<SharingAgreement> sharingAgreements = service.findByPlantId(plantId, status);
        return sharingAgreements.stream()
                .map(SharingAgreementResponse::new)
                .toList();
    }
}
