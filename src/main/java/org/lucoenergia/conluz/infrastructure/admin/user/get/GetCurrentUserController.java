package org.lucoenergia.conluz.infrastructure.admin.user.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.lucoenergia.conluz.infrastructure.admin.user.UserResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Returns the currently authenticated user basic information
 */
@RestController
@RequestMapping("/api/v1/users/current")
public class GetCurrentUserController {

    private final AuthService authService;

    public GetCurrentUserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    @Operation(
            summary = "Get current authenticated user",
            description = "Returns basic information about the currently authenticated user.",
            tags = ApiTag.USERS,
            operationId = "getCurrentUser",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Query executed successfully",
                    useReturnTypeSchema = true
            )
    })
    @UnauthorizedErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> getCurrentUser() {
        Optional<User> currentUser = authService.getCurrentUser();
        // Although the endpoint is protected, in case the authentication is missing/invalid, respond 401
        return currentUser.map(user -> ResponseEntity.ok(new UserResponse(user)))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }
}
