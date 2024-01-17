package org.lucoenergia.conluz.infrastructure.admin.user.disable;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.admin.user.disable.DisableUserService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class DisableUserController {

    private final DisableUserService service;

    public DisableUserController(DisableUserService service) {
        this.service = service;
    }

    @PostMapping(path = "/users/{id}/disable")
    @Operation(
            summary = "Disables a user by ID",
            description = """
                This endpoint is designed to disable a user within the system by specifying the user's unique identifier in the endpoint path.
                
                This operation requires proper authentication, through an authentication token, to ensure secure access.
                
                Upon a successful request, the server responds with an HTTP status code of 200, indicating that the user has been disabled.
                
                The endpoint provides an effective means to temporarily suspend user accounts or restrict access, crucial for security and user management purposes.
                
                In cases where the disablement encounters errors, the server returns an appropriate error status code along with a descriptive error message to guide clients in addressing and resolving the issue.
            """,
            tags = ApiTag.USERS,
            operationId = "disableUser"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User disabled successfully"
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    public void disableUser(@PathVariable("id") UUID userId) {
        service.disable(UserId.of(userId));
    }
}
