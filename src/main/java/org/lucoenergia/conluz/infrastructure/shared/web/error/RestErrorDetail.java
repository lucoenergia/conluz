package org.lucoenergia.conluz.infrastructure.shared.web.error;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

public class RestErrorDetail {

    @Schema(description = "Human-readable error message, in English.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private final String message;

    @Schema(description = "Null for errors not yet migrated to the typed contract; clients " +
            "must fall back to `message` for those.")
    private final RestErrorCode code;

    /**
     * Carries structured data the client cannot already derive from the request it just sent
     * (e.g. a line number in an uploaded file, the offending value, a computed sum). Never
     * repeats an identifier the caller already supplied (e.g. a path variable) - that is noise,
     * not data. Values use invariant formatting: no locale-specific number/date formatting (a
     * BigDecimal keeps its scale, e.g. "0.998765"); localization is a conluz-web concern.
     */
    @Schema(description = "Structured data for this error, keyed in camelCase for use as i18n " +
            "interpolation variables. Null when the error carries no such data.")
    private final Map<String, String> params;

    public RestErrorDetail(String message, RestErrorCode code, Map<String, String> params) {
        this.message = message;
        this.code = code;
        this.params = params;
    }

    public String getMessage() {
        return message;
    }

    public RestErrorCode getCode() {
        return code;
    }

    public Map<String, String> getParams() {
        return params;
    }
}
