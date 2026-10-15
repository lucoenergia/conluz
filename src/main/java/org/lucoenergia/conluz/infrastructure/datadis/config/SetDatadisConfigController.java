package org.lucoenergia.conluz.infrastructure.datadis.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfigurationService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/communities/{communityId}/config/datadis",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class SetDatadisConfigController {

    private final SetDatadisConfigurationService service;

    public SetDatadisConfigController(SetDatadisConfigurationService service) {
        this.service = service;
    }

    @PutMapping
    @Operation(
            summary = "Sets up the Datadis configuration for the specified community.",
            description = """
                    This endpoint creates or replaces the configuration a community uses to connect with the
                    Datadis API.

                    The request body contains:
                    - **username** (required, string): Datadis user.
                    - **password** (optional, string): Datadis password. The password is write-only: it is
                      never returned, only whether one is set (`passwordSet`).
                      - Absent: the stored password is kept and the other fields are updated. If the
                        community has no configuration, or its stored password is empty, the request is
                        rejected with `400` and error code `DATADIS_CONFIG_PASSWORD_REQUIRED`.
                      - A non-empty string: replaces the stored password.
                      - `null`: rejected with `400`. The stored password cannot be cleared.
                      - An empty string: rejected with `400`.
                    - **baseUrl** (required, string): Base URL of the Datadis API (for example
                      `https://datadis.es`).

                    The request body does not carry `enabled`, and a body that does is rejected with `400`. This
                    endpoint never changes whether the Datadis integration is enabled: a new configuration starts
                    disabled, and an existing one keeps its current state. To switch it on or off, use
                    `POST .../config/datadis/enable` and `POST .../config/datadis/disable`. Enabling requires a
                    complete configuration.

                    This configuration is a mandatory step to be able to retrieve consumption data from datadis.

                    Authentication is mandated, utilizing an authentication token, to ensure secure access.
                    **Required: the caller can manage the community** (the community's `canManage` capability).

                    Upon successful request, the server responds with an HTTP status code of 200, along with details
                    about the configuration now stored.

                    A rejected request stores nothing and leaves any existing configuration unchanged. The server
                    responds with `400` when the body is invalid (a missing required field, a `null` or empty
                    password, a blank username, a blank or malformed base URL) or when the password is absent and
                    there is no stored password to keep.
                    """,
            tags = ApiTag.CONSUMPTION,
            operationId = "configureDatadis",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Datadis connection has been successfully configured.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE
                    )
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageCommunity(#communityId)")
    public SetDatadisConfigResponse configureDatadis(@PathVariable UUID communityId,
                                                     @Valid @RequestBody ConfigureDatadisBody body) {
        DatadisConfig config = service.setDatadisConfiguration(communityId, body.mapToSetDatadisConfiguration());
        return SetDatadisConfigResponse.of(config);
    }
}
