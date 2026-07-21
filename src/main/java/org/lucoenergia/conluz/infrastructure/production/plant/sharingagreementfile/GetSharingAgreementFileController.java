package org.lucoenergia.conluz.infrastructure.production.plant.sharingagreementfile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.production.plant.sharingagreementfile.DownloadSharingAgreementFileService;
import org.lucoenergia.conluz.domain.production.plant.sharingagreementfile.SharingAgreementFile;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plants/{plantId}/sharing-agreements/{id}/file")
public class GetSharingAgreementFileController {

    private final DownloadSharingAgreementFileService downloadSharingAgreementFileService;

    public GetSharingAgreementFileController(DownloadSharingAgreementFileService downloadSharingAgreementFileService) {
        this.downloadSharingAgreementFileService = downloadSharingAgreementFileService;
    }

    @GetMapping
    @Operation(
            summary = "Downloads the original distributor file of a sharing agreement",
            description = """
                    Returns the most recently uploaded evidence file of the sharing agreement, unmodified.

                    **Required: any member of the plant's community (any role).**

                    Returns 404 if the plant does not exist, if the caller is not a member of its
                    community, if the sharing agreement does not exist or does not belong to this
                    plant, or if the agreement has no file uploaded, to avoid leaking the existence
                    of plants or agreements by ID.

                    Authentication is required using a Bearer token.
                    """,
            tags = ApiTag.SHARING_AGREEMENTS,
            operationId = "getSharingAgreementFile",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The original distributor file"
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canReadSharingAgreement(#plantId, #id)")
    public ResponseEntity<byte[]> getSharingAgreementFile(@PathVariable UUID plantId, @PathVariable UUID id) {
        SharingAgreementFile file = downloadSharingAgreementFileService.downloadLatestBySharingAgreementId(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", file.getFilename());

        return ResponseEntity.ok()
                .headers(headers)
                .body(file.getContent());
    }
}
