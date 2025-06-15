package org.lucoenergia.conluz.infrastructure.admin.user.logout;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletResponse;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.lucoenergia.conluz.infrastructure.shared.security.auth.AuthResponseHandler;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/logout")
public class LogoutUserController {

    private final AuthService authService;
    private final AuthResponseHandler authResponseHandler;

    public LogoutUserController(AuthService authService, AuthResponseHandler authResponseHandler) {
        this.authService = authService;
        this.authResponseHandler = authResponseHandler;
    }

    @PostMapping
    @Operation(
            summary = "User de-authentication",
            description = """
                    This endpoint handles user logout operations, invalidating the current user session.
                    
                     Upon successful logout, the server invalidates the existing authentication token (JWT)
                     and removes the associated authentication cookie from the client.
    
                     This ensures that subsequent requests can no longer access protected resources
                     using the invalidated credentials.
    
                     The server responds with an HTTP status code of 200 to indicate successful logout.
    
                     In case of any errors during the logout process, the server returns an appropriate
                     error status code along with a descriptive error message.
                    """,
            tags = ApiTag.AUTHENTICATION,
            operationId = "logout"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Logout successful",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        authService.logout();
        // Remove cookie from the response
        authResponseHandler.unsetAccessCookie(response);
        return ResponseEntity.ok().build();
    }
}
