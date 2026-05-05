package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        value = "/api/v1/production/huawei/config",
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
                    This endpoint allows to configure the app to connect with a Huawei FusionSolar
                    Northbound API endpoint.

                    The request body must contain:
                    - **username** (required, string): API user.
                    - **password** (required, string): API password / system code.
                    - **baseUrl** (required, string): Base URL of the Huawei FusionSolar API
                      (for example `https://eu5.fusionsolar.huawei.com/thirdData`). Mock services
                      that emulate the SmartPVMS V6 Northbound Interface can be targeted by
                      providing their base URL here.
                    - **enabled** (required, boolean): Master switch for every Huawei-related
                      service. When `false`, scheduled syncs and aggregation jobs are skipped and
                      the manual sync endpoints respond with `409 Conflict`. Set this to `false`
                      for energy communities that use a different inverter vendor (e.g. Greenheiss).

                    This configuration is a mandatory step to be able to retrieve production data
                    from the Huawei API.

                    Authentication is mandated, utilizing an authentication token, to ensure secure access.
                    **Required Role: ADMIN**

                    Upon successful request, the server responds with an HTTP status code of 200, along with details
                    about the configuration already set.

                    In cases where the creation process encounters errors, the server responds with an appropriate error
                    status code, accompanied by a descriptive error message to guide clients in addressing and resolving the issue.
                    """,
            tags = ApiTag.PRODUCTION,
            operationId = "configureHuawei",
            security = @SecurityRequirement(name = "bearerToken", scopes = {"ADMIN"})
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
    @PreAuthorize("hasRole('ADMIN')")
    public SetHuaweiConfigResponse configureHuawei(@RequestBody ConfigureHuaweiBody body) {
        HuaweiConfig config = service.setHuaweiConfiguration(body.toHuaweiConfig());
        return SetHuaweiConfigResponse.of(config);
    }
}
