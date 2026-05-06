package org.lucoenergia.conluz.infrastructure.consumption.shelly.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.consumption.shelly.config.SetShellyConfigurationService;
import org.lucoenergia.conluz.domain.consumption.shelly.config.ShellyConfig;
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
        value = "/api/v1/consumption/shelly/config",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class SetShellyConfigController {

    private final SetShellyConfigurationService service;

    public SetShellyConfigController(SetShellyConfigurationService service) {
        this.service = service;
    }

    @PutMapping
    @Operation(
            summary = "Sets up the configuration for Shelly integration.",
            description = """
                    This endpoint allows to enable or disable the Shelly integration for the energy community.

                    The request body must contain:
                    - **enabled** (required, boolean): Master switch for every Shelly-related
                      services. When `false`, scheduled jobs for processing MQTT messages and
                      aggregating hourly consumption are skipped. Set this to `false` for energy
                      communities that do not have Shelly meters installed.

                    This configuration is a mandatory step to be able to process Shelly consumption data.

                    Authentication is mandated, utilizing an authentication token, to ensure secure access.
                    **Required Role: ADMIN**

                    Upon successful request, the server responds with an HTTP status code of 200, along with details
                    about the configuration already set.

                    In cases where the creation process encounters errors, the server responds with an appropriate error
                    status code, accompanied by a descriptive error message to guide clients in addressing and resolving the issue.
                    """,
            tags = ApiTag.CONSUMPTION,
            security = @SecurityRequirement(name = "bearerToken", scopes = {"ADMIN"})
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Shelly configuration has been successfully updated.",
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
    public SetShellyConfigResponse configureShelly(@RequestBody ConfigureShellyBody body) {
        ShellyConfig config = service.setShellyConfiguration(body.toShellyConfig());
        return SetShellyConfigResponse.of(config);
    }
}
