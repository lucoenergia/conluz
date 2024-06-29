package org.lucoenergia.conluz.infrastructure.admin.user.login;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletResponse;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.lucoenergia.conluz.domain.admin.user.auth.Token;
import org.lucoenergia.conluz.infrastructure.shared.security.auth.AuthResponseHandler;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/login")
public class LoginUserController {

    private final AuthService authService;
    private final LoginAssembler loginAssembler;
    private final AuthResponseHandler authResponseHandler;

    public LoginUserController(AuthService authService, LoginAssembler loginAssembler, AuthResponseHandler authResponseHandler) {
        this.authService = authService;
        this.loginAssembler = loginAssembler;
        this.authResponseHandler = authResponseHandler;
    }

    @PostMapping
    @Operation(
            summary = "User authentication",
            description = "This endpoint is dedicated to user authentication, requiring clients to provide a valid username and password in the request body. Upon successful authentication, the server generates and returns an authentication token, utilizing JSON Web Tokens (JWT). This token serves as a secure means for subsequent authorized access to protected resources within the system. The server responds with an HTTP status code of 200, along with the generated token. In case of authentication failure or invalid credentials, the server issues an appropriate error status code, accompanied by a descriptive error message.",
            tags = ApiTag.AUTHENTICATION,
            operationId = "login"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Login successful",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    public ResponseEntity<Token> login(@RequestBody LoginRequest body, HttpServletResponse response) {
        Token accessToken = authService.login(loginAssembler.assemble(body));
        // Add cookie to the response
        authResponseHandler.setAccessCookie(response, accessToken);
        return ResponseEntity.ok(accessToken);
    }
}
