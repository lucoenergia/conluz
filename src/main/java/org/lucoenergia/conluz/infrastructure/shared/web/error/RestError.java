package org.lucoenergia.conluz.infrastructure.shared.web.error;

import java.time.OffsetDateTime;

public class RestError {

    private final OffsetDateTime timestamp;
    private final int status;
    private final String message;
    private final String traceId;

    public RestError(int status, String message, String traceId) {
        this.timestamp = OffsetDateTime.now();
        this.traceId = traceId;

        this.status = status;
        this.message = message;
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
}
