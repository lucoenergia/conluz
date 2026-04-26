package org.lucoenergia.conluz.infrastructure.shared.info;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/info", produces = MediaType.APPLICATION_JSON_VALUE)
public class GetInfoController {

    private final GetInfoService getInfoService;

    public GetInfoController(GetInfoService getInfoService) {
        this.getInfoService = getInfoService;
    }

    @GetMapping
    @Operation(
            summary = "Returns application version information.",
            description = "Returns the application version from the build. Does not require authentication.",
            tags = ApiTag.INFO,
            operationId = "getInfo"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Application version info retrieved successfully.",
                    useReturnTypeSchema = true
            )
    })
    @InternalServerErrorResponse
    public ResponseEntity<GetInfoResponse> getInfo() {
        return ResponseEntity.ok(new GetInfoResponse(getInfoService.getVersion()));
    }
}
