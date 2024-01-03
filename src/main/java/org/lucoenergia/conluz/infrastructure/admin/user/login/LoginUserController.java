package org.lucoenergia.conluz.infrastructure.admin.user.login;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.lucoenergia.conluz.domain.admin.user.auth.Token;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
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

    public LoginUserController(AuthService authService, LoginAssembler loginAssembler) {
        this.authService = authService;
        this.loginAssembler = loginAssembler;
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
                    description = "Query executed successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(
                                    value = """
                                            {
                                               "items":[
                                                  {
                                                     "id":"hhtc50AmS9KRqvZuYecV",
                                                     "user":{
                                                         "id":"e7ab39cd-9250-40a9-b829-f11f65aae27d",
                                                         "personalId":"rAtjrSXAU",
                                                         "number":646650705,
                                                         "fullName":"John Doe",
                                                         "address":"Fake Street 123",
                                                         "email":"DzQaM@HDXvc.com",
                                                         "phoneNumber":"+34666333111",
                                                         "enabled":true,
                                                         "role":"PARTNER"
                                                     },
                                                     "name":"My house",
                                                     "address":"Fake Street 123",
                                                     "partitionCoefficient":1.5156003E38,
                                                     "enabled":true
                                                  },
                                                  {
                                                     "id":"mbHX0arnmS4KgooidQxj",
                                                     "user":{
                                                         "id":"81b4de42-a49f-440f-868a-f1cf72199ae7",
                                                         "personalId":"j3E44iio",
                                                         "number":12,
                                                         "fullName":"Alice Cooper",
                                                         "address":"Main Street 123",
                                                         "email":"alice@HDXvc.com",
                                                         "phoneNumber":"+34666555444",
                                                         "enabled":true,
                                                         "role":"PARTNER"
                                                     },
                                                     "name":"Main street 123",
                                                     "address":"Main street 123",
                                                     "partitionCoefficient":2.4035615E38,
                                                     "enabled":true
                                                  },
                                                  {
                                                     "id":"6OOtEWtt4a0epeugj1y2",
                                                     "user":{
                                                         "id":"e7ab39cd-9250-40a9-b829-f11f65aae27d",
                                                         "personalId":"rAtjrSXAU",
                                                         "number":646650705,
                                                         "fullName":"John Doe",
                                                         "address":"Fake Street 123",
                                                         "email":"DzQaM@HDXvc.com",
                                                         "phoneNumber":"+34666333111",
                                                         "enabled":true,
                                                         "role":"PARTNER"
                                                     },
                                                     "name":"The village house",
                                                     "address":"Real street 22",
                                                     "partitionCoefficient":2.4804912E38,
                                                     "enabled":true
                                                  }
                                               ],
                                               "size":10,
                                               "totalElements":3,
                                               "totalPages":1,
                                               "number":0
                                            }
                                            """
                            )
                    )
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    public ResponseEntity<Token> login(@RequestBody LoginRequest body) {
        return ResponseEntity.ok(authService.login(loginAssembler.assemble(body)));
    }
}
