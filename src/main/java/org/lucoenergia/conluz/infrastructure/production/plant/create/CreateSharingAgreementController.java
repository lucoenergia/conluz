package org.lucoenergia.conluz.infrastructure.production.plant.create;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.production.plant.create.CreateSharingAgreementService;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.SharingAgreementResponse;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/plants/{plantId}/sharing-agreements",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Validated
public class CreateSharingAgreementController {

    private final CreateSharingAgreementService service;

    public CreateSharingAgreementController(CreateSharingAgreementService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "Creates a new sharing agreement under a plant",
            description = """
                    This endpoint creates a new DRAFT sharing agreement under the given plant. The
                    agreement's installed power is snapshotted from the plant's current total power at
                    creation time.

                    **Required: Community Admin**

                    Returns 404 if the plant does not exist or the caller is not a member of its
                    community, to avoid leaking the plant's existence.

                    Authentication is required using a Bearer token.
                    """,
            tags = ApiTag.SHARING_AGREEMENTS,
            operationId = "createSharingAgreement",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The sharing agreement has been successfully created.",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageSharingAgreement(#plantId)")
    public SharingAgreementResponse createSharingAgreement(@AuthenticationPrincipal User currentUser,
                                                            @PathVariable UUID plantId,
                                                            @Valid @RequestBody CreateSharingAgreementBody body) {
        SharingAgreement agreement = service.create(plantId, body.getName(), body.getNotes(), currentUser.getId());
        return new SharingAgreementResponse(agreement);
    }
}
