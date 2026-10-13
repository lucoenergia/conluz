package org.lucoenergia.conluz.infrastructure.datadis.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfigurationEnabledService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.config.SetConfigEnabledBody;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/communities/{communityId}/config/datadis/enabled",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class SetDatadisConfigEnabledController {

    private final SetDatadisConfigurationEnabledService service;

    public SetDatadisConfigEnabledController(SetDatadisConfigurationEnabledService service) {
        this.service = service;
    }

    @PutMapping
    @Operation(
            summary = "Enables or disables a community's Datadis configuration.",
            description = """
                    This endpoint switches the community's Datadis configuration on or off, changing nothing else:
                    the stored username, password and base URL are never read from the request nor rewritten.

                    The request body contains:
                    - **enabled** (required, boolean): `true` to enable the configuration, `false` to disable it.

                    Disabling never requires complete data: it succeeds whatever the stored configuration holds,
                    which is how a configuration with missing credentials is switched off.

                    Enabling requires the stored configuration to be complete: a non-blank username, a base URL the
                    configuration write would accept, and a non-empty password. Otherwise the request is rejected
                    with `409` and error code `DATADIS_CONFIG_INCOMPLETE`, and nothing changes. Its
                    `params.missingFields` lists the missing fields, comma-separated, in the order `username`,
                    `baseUrl`, `password` (for example `"baseUrl,password"`). Complete them with the configuration
                    write first.

                    Setting the value the configuration already has succeeds.

                    Authentication is mandated, utilizing an authentication token, to ensure secure access.
                    **Required: the caller can manage the community** (the community's `canManage` capability).

                    Upon successful request, the server responds with an HTTP status code of 204 and no body.

                    A rejected request changes nothing. The server responds with `404` and error code
                    `DATADIS_CONFIG_NOT_FOUND` when the community has no Datadis configuration (none is created),
                    and with `400` when the body is invalid (`enabled` missing or `null`, or an unknown property).
                    """,
            tags = ApiTag.CONSUMPTION,
            operationId = "setDatadisConfigEnabled",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "The Datadis configuration is now enabled or disabled, as requested."
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The configuration cannot be enabled because its stored data is incomplete.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                               "timestamp": "2026-10-14T10:10:25.534035352+02:00",
                                               "status": 409,
                                               "message": "The Datadis configuration cannot be enabled because it is incomplete. Missing fields: baseUrl, password.",
                                               "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                               "errors": [
                                                 {
                                                   "message": "The Datadis configuration cannot be enabled because it is incomplete. Missing fields: baseUrl, password.",
                                                   "code": "DATADIS_CONFIG_INCOMPLETE",
                                                   "params": {
                                                     "missingFields": "baseUrl,password"
                                                   }
                                                 }
                                               ]
                                            }
                                            """
                            )
                    )
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageCommunity(#communityId)")
    public ResponseEntity<Void> setDatadisConfigEnabled(@PathVariable UUID communityId,
                                                        @Valid @RequestBody SetConfigEnabledBody body) {
        service.setEnabled(communityId, body.getEnabled());
        return ResponseEntity.noContent().build();
    }
}
