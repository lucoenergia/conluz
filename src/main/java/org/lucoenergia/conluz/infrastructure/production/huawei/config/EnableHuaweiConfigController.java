package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.production.huawei.config.EnableHuaweiConfigurationService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/plants/{plantId}/production/huawei/config",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class EnableHuaweiConfigController {

    private final EnableHuaweiConfigurationService service;

    public EnableHuaweiConfigController(EnableHuaweiConfigurationService service) {
        this.service = service;
    }

    @PostMapping("/enable")
    @Operation(
            summary = "Enables a plant's Huawei configuration.",
            description = """
                    This endpoint enables the plant's Huawei configuration, changing nothing else: the stored
                    username, password and base URL are not rewritten. The request has no body. It undoes what
                    disabling does to the plant's Huawei services; see `POST .../config/disable`.

                    Enabling requires the stored configuration to be complete: a non-blank username, a base URL the
                    configuration write would accept, and a non-empty password. Otherwise the request is rejected
                    with `409` and error code `HUAWEI_CONFIG_INCOMPLETE`, and nothing changes. Its
                    `params.missingFields` lists the missing fields, comma-separated, in the order `username`,
                    `baseUrl`, `password` (for example `"baseUrl,password"`). Complete them with the configuration
                    write first. Disabling, through its own endpoint, never requires complete data.

                    Enabling a configuration that is already enabled succeeds.

                    Authentication is mandated, utilizing an authentication token, to ensure secure access.
                    **Required: the caller can manage the plant** (the plant's `canManage` capability).

                    Upon successful request, the server responds with an HTTP status code of 200 and no body.

                    A rejected request changes nothing. The server responds with `404` and error code
                    `HUAWEI_CONFIG_NOT_FOUND` when the plant has no Huawei configuration (none is created), and with
                    `400` when the plant id is not a valid UUID.
                    """,
            tags = ApiTag.PRODUCTION,
            operationId = "enableHuaweiConfig",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The Huawei configuration is now enabled."
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
                                               "message": "The Huawei configuration cannot be enabled because it is incomplete. Missing fields: baseUrl, password.",
                                               "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                               "errors": [
                                                 {
                                                   "message": "The Huawei configuration cannot be enabled because it is incomplete. Missing fields: baseUrl, password.",
                                                   "code": "HUAWEI_CONFIG_INCOMPLETE",
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
    @PreAuthorize("@communityAccessGuard.canManagePlant(#plantId)")
    public void enableHuaweiConfig(@PathVariable UUID plantId) {
        service.enable(plantId);
    }
}
