package org.lucoenergia.conluz.infrastructure.consumption.shelly.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.consumption.shelly.config.GetShellyConfigurationService;
import org.lucoenergia.conluz.domain.consumption.shelly.config.ShellyConfig;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping(
        value = "/api/v1/consumption/shelly/config",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class GetShellyConfigController {

    private final GetShellyConfigurationService service;

    public GetShellyConfigController(GetShellyConfigurationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Returns the current Shelly configuration.",
            description = """
                    This endpoint returns the current Shelly integration configuration.

                    Authentication is mandated, utilizing an authentication token, to ensure secure access.
                    **Required Role: ADMIN**

                    Upon successful request, the server responds with an HTTP status code of 200, along with
                    the current configuration. If no configuration has been set yet, a 404 is returned.
                    """,
            tags = ApiTag.CONSUMPTION,
            operationId = "getShellyConfig",
            security = @SecurityRequirement(name = "bearerToken", scopes = {"ADMIN"})
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Current Shelly configuration returned successfully.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE
                    )
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GetShellyConfigResponse> getShellyConfig() {
        Optional<ShellyConfig> config = service.getShellyConfiguration();
        return config
                .map(c -> ResponseEntity.ok(GetShellyConfigResponse.of(c)))
                .orElse(ResponseEntity.notFound().build());
    }
}
