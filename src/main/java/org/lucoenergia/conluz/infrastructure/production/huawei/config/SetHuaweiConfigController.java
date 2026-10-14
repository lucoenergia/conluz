package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/plants/{plantId}/production/huawei/config",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class SetHuaweiConfigController {

    private final SetHuaweiConfigurationService service;

    public SetHuaweiConfigController(SetHuaweiConfigurationService service) {
        this.service = service;
    }

    @PutMapping
    @Operation(
            summary = "Sets up the configuration to be able to connect with Huawei.",
            description = """
                    This endpoint creates or replaces the configuration a plant uses to connect with a
                    Huawei FusionSolar Northbound API endpoint.

                    The request body contains:
                    - **username** (required, string): API user.
                    - **password** (optional, string): API password / system code. The password is
                      write-only: it is never returned, only whether one is set (`passwordSet`).
                      - Absent: the stored password is kept and the other fields are updated. If the
                        plant has no configuration, or its stored password is empty, the request is
                        rejected with `400` and error code `HUAWEI_CONFIG_PASSWORD_REQUIRED`.
                      - A non-empty string: replaces the stored password.
                      - `null`: rejected with `400`. The stored password cannot be cleared.
                      - An empty string: rejected with `400`.
                    - **baseUrl** (required, string): Base URL of the Huawei FusionSolar API
                      (for example `https://localhost:8000/thirdData`). Mock services
                      that emulate the SmartPVMS V6 Northbound Interface can be targeted by
                      providing their base URL here.
                    - **enabled** (required, boolean): Master switch for every Huawei-related
                      service of the plant. When `false`, scheduled syncs and aggregation jobs are
                      skipped and the manual sync endpoints respond with `409 Conflict`. Set this to
                      `false` for plants that use a different inverter vendor (e.g. Greenheiss).

                    To only switch the configuration on or off, use `POST .../config/enable` and
                    `POST .../config/disable`: they change nothing else and take no request body.

                    This configuration is a mandatory step to be able to retrieve the plant's production
                    data from the Huawei API.

                    Authentication is mandated, utilizing an authentication token, to ensure secure access.
                    **Required: the caller can manage the plant** (the plant's `canManage` capability).

                    Upon successful request, the server responds with an HTTP status code of 200, along with details
                    about the configuration now stored.

                    A rejected request stores nothing and leaves any existing configuration unchanged. The server
                    responds with `400` when the body is invalid (a missing required field, a `null` or empty
                    password, a blank username, a malformed base URL) or when the password is absent and there
                    is no stored password to keep.
                    """,
            tags = ApiTag.PRODUCTION,
            operationId = "configureHuawei",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Huawei connection has been successfully configured.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE
                    )
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canManagePlant(#plantId)")
    public SetHuaweiConfigResponse configureHuawei(@PathVariable UUID plantId,
                                                   @Valid @RequestBody ConfigureHuaweiBody body) {
        HuaweiConfig config = service.setHuaweiConfiguration(plantId, body.mapToSetHuaweiConfiguration());
        return SetHuaweiConfigResponse.of(config);
    }
}
