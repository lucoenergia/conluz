package org.lucoenergia.conluz.infrastructure.admin.community.membership.register;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorCode;

@Schema(description = "The result of one data row of an imported file. It carries nothing the file did not hold, "
        + "and is the same whether or not the person already had an account.",
        requiredProperties = {"line", "outcome", "code", "message"})
public class ImportMembersRowResponse {

    public enum Outcome {
        REGISTERED,
        FAILED
    }

    @Schema(description = "The line of the file the row starts on, the header being line 1.", example = "2")
    private final int line;
    @Schema(description = "REGISTERED if the member was registered, with a pending membership; FAILED if nothing "
            + "was registered for this row.")
    private final Outcome outcome;
    @Schema(description = "Why the row failed: MEMBERSHIP_ALREADY_EXISTS if the person already has a membership in "
            + "this community, possibly from an earlier row of the same file; MEMBERSHIP_IMPORT_ROW_INVALID if a "
            + "value breaks a rule of the registration; MEMBERSHIP_IMPORT_ROW_MALFORMED if the row does not have as "
            + "many cells as the header. Null for a registered row.",
            types = {"string", "null"})
    private final RestErrorCode code;
    @Schema(description = "Why the row failed, as a readable message naming the columns and rules broken but never their "
            + "values. Null for a registered row.",
            types = {"string", "null"})
    private final String message;

    public ImportMembersRowResponse(int line, Outcome outcome, RestErrorCode code, String message) {
        this.line = line;
        this.outcome = outcome;
        this.code = code;
        this.message = message;
    }

    public int getLine() {
        return line;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public RestErrorCode getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
