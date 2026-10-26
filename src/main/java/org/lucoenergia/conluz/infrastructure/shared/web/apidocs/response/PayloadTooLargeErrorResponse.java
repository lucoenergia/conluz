package org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.MediaType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(
        responseCode = "413",
        description = """
                The uploaded file is larger than allowed (`REQUEST_PAYLOAD_TOO_LARGE`), so nothing has been done with \
                it. The maximum file size, in bytes, is given in `params.maxFileSizeBytes`.""",
        content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = RestError.class),
                examples = @ExampleObject(
                        value = """
                                {
                                   "timestamp": "2026-10-04T10:10:25.534035352+02:00",
                                   "status": 413,
                                   "message": "The file is larger than the maximum allowed size of 10 MB.",
                                   "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                   "errors": [
                                     {
                                       "message": "The file is larger than the maximum allowed size of 10 MB.",
                                       "code": "REQUEST_PAYLOAD_TOO_LARGE",
                                       "params": {
                                         "maxFileSizeBytes": "10485760"
                                       }
                                     }
                                   ]
                                }
                                """
                )
        )
)
public @interface PayloadTooLargeErrorResponse {
}
