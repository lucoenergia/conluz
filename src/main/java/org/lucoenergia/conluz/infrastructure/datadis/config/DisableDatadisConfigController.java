package org.lucoenergia.conluz.infrastructure.datadis.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.datadis.DisableDatadisConfigurationService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/communities/{communityId}/config/datadis",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class DisableDatadisConfigController {

    private final DisableDatadisConfigurationService service;

    public DisableDatadisConfigController(DisableDatadisConfigurationService service) {
        this.service = service;
    }

    @PostMapping("/disable")
    @Operation(
            summary = "Disables a community's Datadis configuration.",
            description = """
                    This endpoint disables the community's Datadis configuration, changing nothing else: the stored
                    username, password and base URL are not rewritten. The request has no body.

                    Disabling never requires complete data: it succeeds whatever the stored configuration holds,
                    which is how a configuration with missing credentials is switched off.

                    Disabling a configuration that is already disabled succeeds.

                    Authentication is mandated, utilizing an authentication token, to ensure secure access.
                    **Required: the caller can manage the community** (the community's `canManage` capability).

                    Upon successful request, the server responds with an HTTP status code of 204 and no body.

                    A rejected request changes nothing. The server responds with `404` and error code
                    `DATADIS_CONFIG_NOT_FOUND` when the community has no Datadis configuration (none is created), and with
                    `400` when the community id is not a valid UUID.
                    """,
            tags = ApiTag.CONSUMPTION,
            operationId = "disableDatadisConfig",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "The Datadis configuration is now disabled."
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageCommunity(#communityId)")
    public ResponseEntity<Void> disableDatadisConfig(@PathVariable UUID communityId) {
        service.disable(communityId);
        return ResponseEntity.noContent().build();
    }
}
