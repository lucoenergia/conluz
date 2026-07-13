package org.lucoenergia.conluz.infrastructure.shared.web.error;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

public class RestError {

    private final OffsetDateTime timestamp;
    private final int status;

    @Schema(description = "Human-readable summary. When there is exactly one error, equals " +
            "errors[0].message. When there are several (e.g. a batch import with per-line " +
            "errors), this is a short summary (e.g. \"The file contains 5 invalid lines\") - " +
            "never a concatenation of the individual messages. Existing clients that only read " +
            "`message` keep working; new clients should read `errors` for the full, structured " +
            "list. This field exists for backward compatibility and as the fallback for " +
            "clients that do not understand `code`.")
    private final String message;

    private final String traceId;

    @Schema(description = "The individual errors that make up this response. Always has at " +
            "least one element.", requiredMode = Schema.RequiredMode.REQUIRED)
    private final List<RestErrorDetail> errors;

    /**
     * Convenience constructor for the single-message case; derives {@code errors} from
     * {@code message}.
     */
    public RestError(int status, String message, String traceId) {
        this(status, message, traceId, List.of(new RestErrorDetail(message, null, null)));
    }

    public RestError(int status, String message, String traceId, List<RestErrorDetail> errors) {
        if (errors == null || errors.isEmpty()) {
            throw new IllegalArgumentException("errors must not be null or empty");
        }

        this.timestamp = OffsetDateTime.now();
        this.traceId = traceId;

        this.status = status;
        this.message = message;
        this.errors = List.copyOf(errors);
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getTraceId() {
        return traceId;
    }

    public List<RestErrorDetail> getErrors() {
        return errors;
    }
}
