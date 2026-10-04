package org.lucoenergia.conluz.infrastructure.admin.config.init;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.config.init.InitService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
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
            description = "This endpoint serves as a crucial initiation step for the application, allowing the configuration of foundational settings. This endpoint facilitates the establishment of the default admin user credentials, pivotal for initiating subsequent configurations. By executing this endpoint, users can set the groundwork for the app, enabling the seamless configuration of users, supplies, and other application settings. No authorization is required to execute this endpoint, and the response provides confirmation of successful initialization or relevant error messages.\n\nThe body is validated: `defaultAdminUser` and its `personalId`, `fullName`, `email` and `password` are required, and a missing or invalid field is answered 400. The `password` must satisfy the password policy: between 15 and 64 characters, counting each Unicode code point as one, and no more than 72 bytes once UTF-8 encoded; any character is accepted, including spaces and non-ASCII letters, and there are no composition rules. A password that breaks the policy is answered 400 with the `USER_PASSWORD_POLICY_VIOLATION` code and a `rule` parameter naming the rule that failed: `TOO_SHORT`, `TOO_LONG` or `TOO_MANY_BYTES`.\n\nThe application can only be initialised once: any later call is answered 403.",
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
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Conluz has already been initialized.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(
                                    value = """
                                            {
                                               "timestamp": "2026-10-04T10:10:25.534035352+02:00",
                                               "status": 403,
                                               "message": "Admin user already initialized.",
                                               "traceId": "6e602860-80f7-4802-b20f-8b53fb011013"
                                            }
                                            """
                            )
                    )
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    public void init(@Valid @RequestBody InitBody body) {
        initService.init(body.toDefaultAdminUserDomain());
    }
}
