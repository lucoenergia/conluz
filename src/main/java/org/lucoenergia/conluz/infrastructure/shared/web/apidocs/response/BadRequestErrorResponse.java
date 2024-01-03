package org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.MediaType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(
        responseCode = "400",
        description = "The request contains invalid data.",
        content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(
                        value = """
                                {
                                   "timestamp": "2024-01-03T10:10:25.534035352+01:00",
                                   "status": 400,
                                   "message": "The request contains invalid data.",
                                   "traceId": "6e602860-80f7-4802-b20f-8b53fb011013"
                                }
                                """
                )
        )
)
public @interface BadRequestErrorResponse {
}
