package org.lucoenergia.conluz.infrastructure.admin.user.delete;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.user.delete.DeleteUserService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class DeleteUserController {

    private final DeleteUserService service;

    public DeleteUserController(DeleteUserService service) {
        this.service = service;
    }

    @DeleteMapping("/users/{userId}")
    @Operation(
            summary = "Removes a user by ID",
            description = """
                    This endpoint enables the removal of a user from the system by specifying the user's unique identifier within the endpoint path.

                    To utilize this endpoint, clients send a DELETE request with the targeted user's ID, requiring authentication for secure access.
                    **Required: Platform Admin. You cannot delete your own account.**
                    
                    Community admins cannot change another user's account: the account is shared by every community the person belongs to (#415). To remove a member from one community, delete their membership instead (`DELETE /api/v1/communities/{communityId}/memberships/{userId}`); to suspend them, disable it (`POST /api/v1/communities/{communityId}/memberships/{userId}/disable`).

                    Upon successful deletion, the server responds with an HTTP status code of 200, indicating that the user has been successfully removed.

                    In cases where the deletion process encounters errors, the server returns an appropriate error status code, along with a descriptive error message to guide clients in diagnosing and addressing the issue.
                """,
            tags = ApiTag.USERS,
            operationId = "deleteUser",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User deleted successfully"
            )
    })
    @ForbiddenAccountChangeErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canDeleteUser(#userId)")
    public void deleteUser(@PathVariable("userId") UUID userId) {
        service.delete(UserId.of(userId));
    }
}
