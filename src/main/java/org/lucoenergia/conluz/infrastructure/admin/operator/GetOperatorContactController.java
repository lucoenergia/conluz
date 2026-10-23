package org.lucoenergia.conluz.infrastructure.admin.operator;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.admin.operator.GetOperatorContactService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/operator-contact", produces = MediaType.APPLICATION_JSON_VALUE)
public class GetOperatorContactController {

    private final GetOperatorContactService service;

    public GetOperatorContactController(GetOperatorContactService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Returns the contact of the organisation operating this instance.",
            description = """
                    Returns how to reach the organisation operating this instance: the last-resort contact for \
                    someone who cannot sign in and so has no community to turn to. `contact` is null if this \
                    instance configures none.

                    Does not require authentication: any token sent is ignored.
                    """,
            tags = ApiTag.INFO,
            operationId = "getOperatorContact"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The operator contact, or a null contact if none is configured.",
                    useReturnTypeSchema = true
            )
    })
    @InternalServerErrorResponse
    public GetOperatorContactResponse getOperatorContact() {
        return GetOperatorContactResponse.of(service.getOperatorContact());
    }
}
