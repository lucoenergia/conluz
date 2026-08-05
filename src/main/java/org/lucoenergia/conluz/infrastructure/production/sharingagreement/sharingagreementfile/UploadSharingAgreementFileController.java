package org.lucoenergia.conluz.infrastructure.production.sharingagreement.sharingagreementfile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.DistributorFileStoreResult;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.StoreDistributorFileService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/plants/{plantId}/sharing-agreements/{sharingAgreementId}/file",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class UploadSharingAgreementFileController {

    private final StoreDistributorFileService service;

    public UploadSharingAgreementFileController(StoreDistributorFileService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "Uploads a distributor coefficient-partition file for a DRAFT sharing agreement",
            description = """
                    Validates the uploaded file, stores it as evidence on the agreement, and materialises
                    its parsed entries as pending partition coefficients (not yet active). Re-uploading
                    atomically replaces the agreement's entire pending coefficient set.

                    **Required: Community Admin**

                    Returns 404 if the plant or the agreement does not exist, does not belong to this
                    plant, or the caller is not a member of its community, to avoid leaking existence.
                    Returns 409 if the agreement is not in DRAFT status.
                    Returns 400 with a collection of typed errors, one per violated file rule, if the
                    file fails validation.

                    Authentication is required using a Bearer token.
                    """,
            tags = ApiTag.SHARING_AGREEMENTS,
            operationId = "uploadSharingAgreementFile",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The file was validated, stored and its entries materialised.",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The file failed validation: one or more of its lines, or the file " +
                            "as a whole, violate a distributor file rule (e.g. malformed filename, " +
                            "unknown or duplicate CUPS, invalid coefficient value, coefficient sum not " +
                            "equal to 1). `errors` lists every violation found, not just the first.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = RestError.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The agreement is not in DRAFT status.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = RestError.class))
            )
    })
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageSharingAgreement(#plantId, #sharingAgreementId)")
    public UploadSharingAgreementFileResponse uploadSharingAgreementFile(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID plantId,
            @PathVariable UUID sharingAgreementId,
            @Parameter(description = "Distributor TXT file. Format: CUPS;coefficient (comma decimal separator). " +
                    "File name must follow the pattern: {regulatoryCode}_{YYYY}.txt.", required = true)
            @RequestParam("file") MultipartFile file) throws IOException {
        DistributorFileStoreResult result = service.store(plantId, sharingAgreementId, file.getOriginalFilename(), file.getBytes(),
                currentUser.getId());

        return new UploadSharingAgreementFileResponse(result);
    }
}
