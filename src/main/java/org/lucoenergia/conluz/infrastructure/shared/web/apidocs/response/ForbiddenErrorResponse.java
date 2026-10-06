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
        responseCode = "403",
        description = """
                You are not authorized to make this request, or your password must be changed first \
                (`USER_PASSWORD_CHANGE_REQUIRED`): while `mustChangePassword` is true on `GET /api/v1/users/current`, \
                every request other than reading the current user, changing the password and logging out is refused.""",
        content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = RestError.class),
                examples = {
                        @ExampleObject(
                                name = "Not authorized",
                                value = """
                                        {
                                           "timestamp": "2024-01-03T10:10:25.534035352+01:00",
                                           "status": 403,
                                           "message": "You are not authorized to make this request.",
                                           "traceId": "6e602860-80f7-4802-b20f-8b53fb011013"
                                        }
                                        """
                        ),
                        @ExampleObject(
                                name = "Password change required",
                                value = """
                                        {
                                           "timestamp": "2026-10-06T10:10:25.534035352+02:00",
                                           "status": 403,
                                           "message": "You must change your password before you can continue.",
                                           "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                           "errors": [
                                             {
                                               "message": "You must change your password before you can continue.",
                                               "code": "USER_PASSWORD_CHANGE_REQUIRED",
                                               "params": null
                                             }
                                           ]
                                        }
                                        """
                        )
                }
        )
)
public @interface ForbiddenErrorResponse {
}
