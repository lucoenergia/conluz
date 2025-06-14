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
                    This endpoint is dedicated to user authentication, requiring clients to provide a valid username and password in the request body.
                    
                    Upon successful authentication, the server generates and returns an authentication token, utilizing JSON Web Tokens (JWT).
                    
                    This token serves as a secure means for subsequent authorized access to protected resources within the system.
                    
                    The server responds with an HTTP status code of 200, along with the generated token.
                    
                    In case of authentication failure or invalid credentials, the server issues an appropriate error status code, accompanied by a descriptive error message.
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
