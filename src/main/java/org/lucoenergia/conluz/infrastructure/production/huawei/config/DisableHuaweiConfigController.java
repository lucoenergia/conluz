package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.production.huawei.config.DisableHuaweiConfigurationService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
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
public class DisableHuaweiConfigController {

    private final DisableHuaweiConfigurationService service;

    public DisableHuaweiConfigController(DisableHuaweiConfigurationService service) {
        this.service = service;
    }

    @PostMapping("/disable")
    @Operation(
            summary = "Disables a plant's Huawei configuration.",
            description = """
                    This endpoint disables the plant's Huawei configuration, changing nothing else: the stored
                    username, password and base URL are not rewritten. The request has no body.

                    Disabling never requires complete data: it succeeds whatever the stored configuration holds,
                    which is how a configuration with missing credentials is switched off.

                    Disabling a configuration that is already disabled succeeds.

                    Authentication is mandated, utilizing an authentication token, to ensure secure access.
                    **Required: the caller can manage the plant** (the plant's `canManage` capability).

                    Upon successful request, the server responds with an HTTP status code of 200 and no body.

                    A rejected request changes nothing. The server responds with `404` and error code
                    `HUAWEI_CONFIG_NOT_FOUND` when the plant has no Huawei configuration (none is created), and with
                    `400` when the plant id is not a valid UUID.
                    """,
            tags = ApiTag.PRODUCTION,
            operationId = "disableHuaweiConfig",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The Huawei configuration is now disabled."
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canManagePlant(#plantId)")
    public void disableHuaweiConfig(@PathVariable UUID plantId) {
        service.disable(plantId);
    }
}
