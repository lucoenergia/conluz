package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorCode;

import java.util.List;

@Schema(description = "The result of one data row of an imported file of supplies. It carries nothing the file held: "
        + "neither the CUPS nor the personal ID.",
        requiredProperties = {"line", "outcome", "code", "message", "errors"})
public class ImportSuppliesRowResponse {

    public enum Outcome {
        CREATED,
        FAILED
    }

    @Schema(description = "The line of the file the row starts on, the header being line 1.", example = "2")
    private final int line;
    @Schema(description = "CREATED if the supply was created; FAILED if nothing was created for this row.")
    private final Outcome outcome;
    @Schema(description = "Why the row failed: SUPPLY_IMPORT_ROW_INVALID if a value breaks a rule of the import; "
            + "SUPPLY_IMPORT_ROW_MALFORMED if the row does not have as many cells as the header; "
            + "SUPPLY_OWNER_NOT_FOUND if the personal ID matches no member of this community, whether or not it "
            + "matches a user; SUPPLY_ALREADY_EXISTS if a supply of this community already has the same CUPS, "
            + "possibly from an earlier row of the same file; SUPPLY_REGISTERED_IN_ANOTHER_COMMUNITY if only a supply "
            + "of another community has it. Null for a created row.",
            types = {"string", "null"})
    private final RestErrorCode code;
    @Schema(description = "Why the row failed, as a readable message in the language of the request's "
            + "Accept-Language, naming the columns and rules broken but never their values. Null for a created row.",
            types = {"string", "null"})
    private final String message;
    @Schema(description = "For SUPPLY_IMPORT_ROW_INVALID, each rule broken, in the order of the columns, so that a "
            + "client can write its own text; the same rules as `message`. Empty for any other row.")
    private final List<ImportSuppliesRowErrorResponse> errors;

    public ImportSuppliesRowResponse(int line, Outcome outcome, RestErrorCode code, String message,
                                     List<ImportSuppliesRowErrorResponse> errors) {
        this.line = line;
        this.outcome = outcome;
        this.code = code;
        this.message = message;
        this.errors = List.copyOf(errors);
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

    public List<ImportSuppliesRowErrorResponse> getErrors() {
        return errors;
    }
}
