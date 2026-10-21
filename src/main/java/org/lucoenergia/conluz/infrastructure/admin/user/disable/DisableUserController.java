package org.lucoenergia.conluz.infrastructure.admin.user.disable;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.user.disable.DisableUserService;
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
public class DisableUserController {

    private final DisableUserService service;

    public DisableUserController(DisableUserService service) {
        this.service = service;
    }

    @PostMapping(path = "/users/{userId}/disable")
    @Operation(
            summary = "Disables a user by ID",
            description = """
                This endpoint is designed to disable a user within the system by specifying the user's unique identifier in the endpoint path.
                
                This operation requires proper authentication, through an authentication token, to ensure secure access.
                **Required: nobody may use it on another user's account, and you cannot disable your own account.** Nobody may disable another user's account: neither a Community Admin, whatever their role in the user's communities (#415), nor a Platform Admin (#446). A caller who cannot see the user is answered 404; one who can is answered 403.
                
                The account is shared by every community the person belongs to (#415). To suspend a member in one community, enable or disable their membership instead (`POST /api/v1/communities/{communityId}/memberships/{userId}/enable` or `/disable`).
                
                Upon a successful request, the server responds with an HTTP status code of 200, indicating that the user has been disabled.
                
                The endpoint provides an effective means to temporarily suspend user accounts or restrict access, crucial for security and user management purposes.
                
                In cases where the disablement encounters errors, the server returns an appropriate error status code along with a descriptive error message to guide clients in addressing and resolving the issue.
            """,
            tags = ApiTag.USERS,
            operationId = "disableUser",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User disabled successfully"
            )
    })
    @ForbiddenAccountChangeErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canDisableUser(#userId)")
    public void disableUser(@PathVariable("userId") UUID userId) {
        service.disable(UserId.of(userId));
    }
}
