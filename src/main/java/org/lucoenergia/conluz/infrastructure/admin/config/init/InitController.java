package org.lucoenergia.conluz.infrastructure.admin.config.init;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.admin.config.init.InitService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        value = "/api/v1/init",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class InitController {

    private final InitService initService;

    public InitController(InitService initService) {
        this.initService = initService;
    }

    @PostMapping
    @Operation(
            summary = "Sets up the initial configuration for the app.",
            description = "This endpoint serves as a crucial initiation step for the application, allowing the configuration of foundational settings. This endpoint facilitates the establishment of the default admin user credentials, pivotal for initiating subsequent configurations. By executing this endpoint, users can set the groundwork for the app, enabling the seamless configuration of users, supplies, and other application settings. No authorization is required to execute this endpoint, and the response provides confirmation of successful initialization or relevant error messages.",
            tags = ApiTag.CONFIGURATION,
            operationId = "init"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Conluz has been successfully initialized.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE
                    )
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    public void init(@RequestBody InitBody body) {
        initService.init(body.toDefaultAdminUserDomain());
    }
}
