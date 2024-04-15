package org.lucoenergia.conluz.infrastructure.consumption.datadis.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.admin.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.admin.datadis.SetDatadisConfigurationService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(
        value = "/api/v1/consumption/datadis/config",
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
            summary = "Sets up the configuration to be able to connect with Datadis.",
            description = """
                    This endpoint allows to configure the app to connect with datadis.es.
                    This configuration is a mandatory step to be able to retrieve consumption data from datadis.es.
                    Authentication is mandated, utilizing an authentication token, to ensure secure access.
                    Upon successful request, the server responds with an HTTP status code of 200, along with details about the configuration already set.
                    In cases where the creation process encounters errors, the server responds with an appropriate error status code, accompanied by a descriptive error message to guide clients in addressing and resolving the issue.
                    """,
            tags = ApiTag.CONFIGURATION,
            operationId = "configureDatadis"
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
    public SetDatadisConfigResponse configureDatadis(@RequestBody ConfigureDatadisBody body) {
        DatadisConfig config = service.setDatadisConfiguration(body.toDatadisConfig());
        return SetDatadisConfigResponse.of(config);
    }
}
