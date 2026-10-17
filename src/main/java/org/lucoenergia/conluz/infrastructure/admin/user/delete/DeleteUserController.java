package org.lucoenergia.conluz.infrastructure.admin.user.delete;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.user.delete.DeleteUserService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.*;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.MediaType;
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

                    Deleting the account also deletes every membership of the user, in every community, with its member record, and every token of the user. Memberships the user invited are kept, with no inviter, and sharing agreements and distributor files the user created, edited or uploaded are kept, with no creator, editor or uploader.

                    The account of a user who owns supplies, in any community, is not deleted: the request is answered 409 with `USER_OWNS_SUPPLIES`, and nothing is deleted. To stop their access, disable the account instead (`POST /api/v1/users/{userId}/disable`). The last enabled platform admin is not deleted either (409 `USER_LAST_PLATFORM_ADMIN`).

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
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The request conflicts with the current state: the user owns supplies, or is the last enabled platform admin. Nothing is deleted. To stop the access of a supply owner, disable the account instead.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Owns supplies",
                                            value = """
                                                    {
                                                       "timestamp": "2026-10-17T10:10:25.534035352+02:00",
                                                       "status": 409,
                                                       "message": "The user with ID '0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1' owns supplies, so their account cannot be deleted. To stop their access, disable the account instead.",
                                                       "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                                       "errors": [
                                                         {
                                                           "message": "The user with ID '0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1' owns supplies, so their account cannot be deleted. To stop their access, disable the account instead.",
                                                           "code": "USER_OWNS_SUPPLIES",
                                                           "params": {
                                                             "userId": "0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1"
                                                           }
                                                         }
                                                       ]
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Last platform admin",
                                            value = """
                                                    {
                                                       "timestamp": "2026-10-17T10:10:25.534035352+02:00",
                                                       "status": 409,
                                                       "message": "Cannot perform this action as it would leave the system with zero platform admins.",
                                                       "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                                       "errors": [
                                                         {
                                                           "message": "Cannot perform this action as it would leave the system with zero platform admins.",
                                                           "code": "USER_LAST_PLATFORM_ADMIN",
                                                           "params": null
                                                         }
                                                       ]
                                                    }
                                                    """
                                    )
                            }
                    )
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
