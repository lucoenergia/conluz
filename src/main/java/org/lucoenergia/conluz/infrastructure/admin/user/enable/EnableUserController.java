package org.lucoenergia.conluz.infrastructure.admin.user.enable;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.user.enable.EnableUserService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenAccountChangeErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
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

    @PostMapping(path = "/users/{userId}/enable")
    @Operation(
            summary = "Enables a user by ID",
            description = """
                    This endpoint serves the purpose of enabling a previously disabled user within the system, with the user's unique identifier specified in the endpoint path.
                    
                    Proper authentication, through an authentication token, is required for secure access.
                    **Required: nobody may use it on another user's account, and you cannot enable your own account.** Nobody may enable another user's account: neither a Community Admin, whatever their role in the user's communities (#415), nor a Platform Admin (#446). A caller who cannot see the user is answered 404; one who can is answered 403.
                    
                    The account is shared by every community the person belongs to (#415). To suspend a member in one community, enable or disable their membership instead (`POST /api/v1/communities/{communityId}/memberships/{userId}/enable` or `/disable`).
                    
                    Upon a successful request, the server responds with an HTTP status code of 200, indicating that the user has been successfully enabled.
                    
                    This endpoint provides a crucial mechanism for restoring user access or lifting restrictions, supporting effective user management.
                    
                    In situations where the enabling process encounters errors, the server responds with an appropriate error status code, accompanied by a descriptive error message to assist clients in diagnosing and resolving the issue.""",
            tags = ApiTag.USERS,
            operationId = "enableUser",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User enabled successfully"
            )
    })
    @ForbiddenAccountChangeErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canEnableUser(#userId)")
    public void enableUser(@PathVariable("userId") UUID userId) {
        service.enable(UserId.of(userId));
    }
}
