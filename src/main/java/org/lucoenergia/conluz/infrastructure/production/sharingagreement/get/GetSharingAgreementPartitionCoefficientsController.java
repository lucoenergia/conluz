package org.lucoenergia.conluz.infrastructure.production.sharingagreement.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementPartitionCoefficientsService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/plants/{plantId}/sharing-agreements/{sharingAgreementId}/partition-coefficients",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class GetSharingAgreementPartitionCoefficientsController {

    private final GetSharingAgreementPartitionCoefficientsService service;

    public GetSharingAgreementPartitionCoefficientsController(GetSharingAgreementPartitionCoefficientsService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves the full partition-coefficient set of a sharing agreement",
            description = """
                    Returns one row per supply of the agreement's coefficient set, enriched with the
                    supply's CUPS code and name, and two server-computed fields (applicationState,
                    endState) so callers never need to derive interval/successor logic themselves.
                    Ordered by CUPS ascending. Works regardless of the agreement's status (DRAFT,
                    PUBLISHED or SUPERSEDED).

                    **Required: Community Admin of the plant's community.**

                    Returns 404 if the plant does not exist, if the caller is not a member of its
                    community, or if the sharing agreement does not exist or does not belong to this
                    plant, to avoid leaking the existence of plants or agreements by ID. Returns 403
                    if the caller is a member of the community but not an admin.

                    Authentication is required using a Bearer token.
                    """,
            tags = ApiTag.SHARING_AGREEMENTS,
            operationId = "getSharingAgreementPartitionCoefficients",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Coefficient set retrieved successfully",
                    useReturnTypeSchema = true
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageSharingAgreement(#plantId, #sharingAgreementId)")
    public List<SharingAgreementPartitionCoefficientResponse> getPartitionCoefficients(
            @PathVariable UUID plantId, @PathVariable UUID sharingAgreementId) {
        return service.findBySharingAgreementId(plantId, sharingAgreementId).stream()
                .map(SharingAgreementPartitionCoefficientResponse::new)
                .toList();
    }
}
