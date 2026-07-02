package org.lucoenergia.conluz.infrastructure.datadis.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.GetDatadisConfigurationService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/communities/{communityId}/config/datadis",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class GetDatadisConfigController {

    private final GetDatadisConfigurationService service;

    public GetDatadisConfigController(GetDatadisConfigurationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Returns the Datadis configuration for the specified community.",
            description = """
                    This endpoint returns the Datadis connection configuration for a specific community.

                    The password is never returned in the response. Instead, a boolean field
                    `passwordSet` indicates whether a password has been configured.

                    Authentication is mandated, utilizing an authentication token, to ensure secure access.
                    **Required: Community Admin**

                    Upon successful request, the server responds with an HTTP status code of 200, along with
                    the current configuration. If no configuration has been set yet, a 404 is returned.
                    """,
            tags = ApiTag.CONSUMPTION,
            operationId = "getDatadisConfig",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Current Datadis configuration returned successfully.",
                    useReturnTypeSchema = true
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageCommunity(#communityId)")
    public ResponseEntity<GetDatadisConfigResponse> getDatadisConfig(@PathVariable UUID communityId) {
        Optional<DatadisConfig> config = service.findByCommunityId(communityId);
        return config
                .map(c -> ResponseEntity.ok(GetDatadisConfigResponse.of(c)))
                .orElse(ResponseEntity.notFound().build());
    }
}
