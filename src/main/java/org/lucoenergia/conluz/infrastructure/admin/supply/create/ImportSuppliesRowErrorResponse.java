package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.supply.create.SupplyImportConstraint;

import java.util.Map;

@Schema(description = "A rule of the import that a value of the row breaks. It names the column and the rule, never "
        + "the value.",
        requiredProperties = {"column", "constraint", "params"})
public class ImportSuppliesRowErrorResponse {

    @Schema(description = "The header of the column whose value breaks the rule.", example = "code")
    private final String column;
    @Schema(description = "The rule broken: REQUIRED if a required value is empty or blank; INVALID_CUPS if the code, "
            + "once upper-cased, holds whitespace or is neither 20 nor 22 characters long. A newer server may add "
            + "rules, so a client must also handle one it does not know.",
            example = "INVALID_CUPS")
    private final SupplyImportConstraint constraint;
    @Schema(description = "The rule's parameters, keyed in camelCase for use as i18n interpolation variables and "
            + "formatted without a locale. Empty if the rule has none, as both rules today.",
            example = "{}")
    private final Map<String, String> params;

    public ImportSuppliesRowErrorResponse(String column, SupplyImportConstraint constraint,
                                          Map<String, String> params) {
        this.column = column;
        this.constraint = constraint;
        this.params = Map.copyOf(params);
    }

    public String getColumn() {
        return column;
    }

    public SupplyImportConstraint getConstraint() {
        return constraint;
    }

    public Map<String, String> getParams() {
        return params;
    }
}
