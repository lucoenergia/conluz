package org.lucoenergia.conluz.infrastructure.admin.user.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserService;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.lucoenergia.conluz.infrastructure.admin.user.UserResponse;
import org.lucoenergia.conluz.infrastructure.shared.pagination.PaginationRequestMapper;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springdoc.core.converters.models.PageableAsQueryParam;
import org.springframework.data.domain.Pageable;
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
    private final PaginationRequestMapper paginationRequestMapper;

    public GetAllUsersController(GetUserService service, PaginationRequestMapper paginationRequestMapper) {
        this.service = service;
        this.paginationRequestMapper = paginationRequestMapper;
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
                    useReturnTypeSchema = true
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @PageableAsQueryParam
    public PagedResult<UserResponse> getAllUsers(@Parameter(hidden = true) Pageable page) {
            PagedResult<User> users = service.findAll(paginationRequestMapper.mapRequest(page));

        List<UserResponse> responseUsers = users.getItems().stream()
                .map(UserResponse::new).toList();

        return new PagedResult<>(responseUsers, users.getSize(), users.getTotalElements(), users.getTotalPages(),
                users.getNumber());
    }
}
