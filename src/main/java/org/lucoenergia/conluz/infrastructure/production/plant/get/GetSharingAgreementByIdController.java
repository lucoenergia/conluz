package org.lucoenergia.conluz.infrastructure.production.plant.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.production.plant.SharingAgreement;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/plants/{plantId}/sharing-agreements/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
public class GetSharingAgreementByIdController {

    private final GetSharingAgreementService service;

    public GetSharingAgreementByIdController(GetSharingAgreementService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves a single sharing agreement by ID",
            description = """
                    This endpoint retrieves detailed information about a specific sharing agreement of a plant.

                    **Required: any member of the plant's community (any role).**

                    Returns 404 if the plant does not exist, if the caller is not a member of its
                    community, or if the sharing agreement does not exist or does not belong to
                    this plant, to avoid leaking the existence of plants or agreements by ID.

                    Authentication is required using a Bearer token.
                    """,
            tags = ApiTag.SHARING_AGREEMENTS,
            operationId = "getSharingAgreementById",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Sharing agreement found and returned successfully",
                    useReturnTypeSchema = true
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canReadSharingAgreement(#plantId, #id)")
    public SharingAgreementResponse getSharingAgreementById(@PathVariable UUID plantId, @PathVariable UUID id) {
        SharingAgreement sharingAgreement = service.findById(id);
        return new SharingAgreementResponse(sharingAgreement);
    }
}
