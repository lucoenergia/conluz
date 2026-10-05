package org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response;

import io.swagger.v3.oas.annotations.headers.Header;
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
        responseCode = "429",
        description = """
                Too many failed attempts on this account or from this client address \
                (`AUTH_TOO_MANY_FAILED_ATTEMPTS`). The password was not checked. Retry after the number of \
                seconds in the `Retry-After` header, also given in `params.retryAfterSeconds`.""",
        headers = @Header(
                name = "Retry-After",
                description = "Seconds, rounded up, until an attempt will be accepted again.",
                required = true,
                schema = @Schema(type = "integer", format = "int64")
        ),
        content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = RestError.class),
                examples = @ExampleObject(
                        value = """
                                {
                                   "timestamp": "2026-10-04T10:10:25.534035352+02:00",
                                   "status": 429,
                                   "message": "Too many failed attempts. Please wait before trying again.",
                                   "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                   "errors": [
                                     {
                                       "message": "Too many failed attempts. Please wait before trying again.",
                                       "code": "AUTH_TOO_MANY_FAILED_ATTEMPTS",
                                       "params": {
                                         "retryAfterSeconds": "843"
                                       }
                                     }
                                   ]
                                }
                                """
                )
        )
)
public @interface TooManyRequestsErrorResponse {
}
