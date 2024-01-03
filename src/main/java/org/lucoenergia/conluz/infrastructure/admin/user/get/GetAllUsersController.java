package org.lucoenergia.conluz.infrastructure.admin.user.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserService;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.lucoenergia.conluz.infrastructure.admin.user.UserResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Get all users
 */
@RestController
@RequestMapping(
        value = "/api/v1/users",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class GetAllUsersController {

    private final GetUserService service;

    public GetAllUsersController(GetUserService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves all registered users in the system with support for pagination, filtering, and sorting.",
            description = "This endpoint facilitates the retrieval of all users within the system, allowing clients to access a comprehensive list of user details. Users can include optional query parameters, such as page to specify the page number, limit to determine the number of users per page, filter for selective retrieval based on specific criteria, and sort to define the order of the results. Proper authentication, through an authentication token, is required for secure access to this endpoint. A successful request returns an HTTP status code of 200, along with a paginated list of user details, providing valuable information such as unique identifiers, usernames, and creation timestamps. In case of issues, the server responds with an appropriate error status code, accompanied by a descriptive error message to guide clients in addressing any problems encountered during the retrieval process.",
            tags = ApiTag.USERS,
            operationId = "getAllUsers"
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
                                               "items": [
                                                 {
                                                   "id": "a2bf32b5-4537-4e02-b9df-c87908e5418e",
                                                   "personalId": "01234567Z",
                                                   "number": 0,
                                                   "fullName": "Energy Community Acme",
                                                   "address": "Fake Street 123",
                                                   "email": "acmecom@email.com",
                                                   "phoneNumber": null,
                                                   "enabled": true,
                                                   "role": "ADMIN"
                                                 },
                                                 {
                                                   "id": "fb13c5c6-5f6a-41d3-97a2-a94fc73d7385",
                                                   "personalId": "KvKnvXPfU",
                                                   "number": 21,
                                                   "fullName": "John Doe",
                                                   "address": "Main Street 22",
                                                   "email": "johndoe@kBikj.com",
                                                   "phoneNumber": "+34666333111",
                                                   "enabled": false,
                                                   "role": "PARTNER"
                                                 }
                                               ],
                                               "size": 10,
                                               "totalElements": 2,
                                               "totalPages": 1,
                                               "number": 0
                                             }
                                            """
                            )
                    )
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    public PagedResult<UserResponse> getAllUsers(PagedRequest page) {
        PagedResult<User> users = service.findAll(page);

        List<UserResponse> responseUsers = users.getItems().stream()
                .map(UserResponse::new).toList();

        return new PagedResult<>(responseUsers, users.getSize(), users.getTotalElements(), users.getTotalPages(),
                users.getNumber());
    }
}
