package org.lucoenergia.conluz.infrastructure.admin.user.enable;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.user.enable.EnableUserService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class EnableUserController {

    private final EnableUserService service;

    public EnableUserController(EnableUserService service) {
        this.service = service;
    }

    @PostMapping(path = "/users/{id}/enable")
    @Operation(
            summary = "Enables a user by ID",
            description = """
                    This endpoint serves the purpose of enabling a previously disabled user within the system, with the user's unique identifier specified in the endpoint path.
                    
                    Proper authentication, through an authentication token, is required for secure access.
                    **Required Role: ADMIN**
                    
                    Upon a successful request, the server responds with an HTTP status code of 200, indicating that the user has been successfully enabled.
                    
                    This endpoint provides a crucial mechanism for restoring user access or lifting restrictions, supporting effective user management.
                    
                    In situations where the enabling process encounters errors, the server responds with an appropriate error status code, accompanied by a descriptive error message to assist clients in diagnosing and resolving the issue.""",
            tags = ApiTag.USERS,
            operationId = "disableUser",
            security = @SecurityRequirement(name = "bearerToken", scopes = {"ADMIN"})
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
    @PreAuthorize("hasRole('ADMIN')")
    public void enableUser(@PathVariable("id") UUID userId) {
        service.enable(UserId.of(userId));
    }
}
