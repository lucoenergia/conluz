package org.lucoenergia.conluz.infrastructure.production.sharingagreement.generatefile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.production.sharingagreement.generatefile.GenerateSharingAgreementFileService;
import org.lucoenergia.conluz.domain.production.sharingagreement.generatefile.GeneratedDistributorFile;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/plants/{plantId}/sharing-agreements/{sharingAgreementId}/generate-file",
        consumes = MediaType.APPLICATION_JSON_VALUE
)
@Validated
public class GenerateSharingAgreementFileController {

    private final GenerateSharingAgreementFileService service;

    public GenerateSharingAgreementFileController(GenerateSharingAgreementFileService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "Generates the i-DE distributor coefficient-partition file for a sharing agreement",
            description = """
                    Builds the i-DE distributor TXT file from this agreement's complete, immutable
                    partition-coefficient set -- one row per supply, regardless of status or
                    activation state -- and streams it back as a file download. Nothing is
                    persisted -- the file is built in memory from the current state and returned.

                    **Required: Community Admin**

                    Allowed for any agreement status (DRAFT, PUBLISHED, SUPERSEDED).

                    Returns 404 if the plant or the agreement does not exist, does not belong to this
                    plant, or the caller is not a member of its community, to avoid leaking existence.
                    Returns 409 if the plant has no regulatory code (CAU) configured, or if the
                    agreement's coefficient set does not sum to exactly 1.

                    Authentication is required using a Bearer token.
                    """,
            tags = ApiTag.SHARING_AGREEMENTS,
            operationId = "generateSharingAgreementDistributorFile",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The generated distributor file.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary")
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The plant has no regulatory code configured, or the agreement's coefficient set does not sum to exactly 1.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = RestError.class))
            )
    })
    @BadRequestErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageSharingAgreement(#plantId, #sharingAgreementId)")
    public ResponseEntity<byte[]> generateFile(
            @PathVariable UUID plantId,
            @PathVariable UUID sharingAgreementId,
            @Valid @RequestBody GenerateDistributorFileBody body) {

        GeneratedDistributorFile file = service.generate(plantId, sharingAgreementId, body.getYear());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment().filename(file.getFilename()).build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(file.getContent());
    }
}
