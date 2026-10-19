package org.lucoenergia.conluz.infrastructure.admin.community.membership.register;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.membership.register.MemberImportConstraint;

import java.util.Map;

@Schema(description = "A rule of the registration that a value of the row breaks. It names the column and the rule, "
        + "never the value.",
        requiredProperties = {"column", "constraint", "params"})
public class ImportMembersRowErrorResponse {

    @Schema(description = "The header of the column whose value breaks the rule.", example = "memberNumber")
    private final String column;
    @Schema(description = "The rule broken: REQUIRED if a required value is empty or blank; NOT_EMPTY_ONCE_NORMALISED "
            + "if the personal ID holds only whitespace, dots and hyphens; INVALID_PERSONAL_ID if the personal ID, once "
            + "normalised, is not a valid DNI, NIE or NIF; MIN if the number is below `params.min`; "
            + "EMAIL if the value is not an email address; NOT_AN_INTEGER if the value is not a whole number. A newer "
            + "server may add rules, so a client must also handle one it does not know.",
            example = "MIN")
    private final MemberImportConstraint constraint;
    @Schema(description = "The rule's parameters, keyed in camelCase for use as i18n interpolation variables and "
            + "formatted without a locale: `min` for MIN. Empty if the rule has none.",
            example = "{\"min\": \"0\"}")
    private final Map<String, String> params;

    public ImportMembersRowErrorResponse(String column, MemberImportConstraint constraint, Map<String, String> params) {
        this.column = column;
        this.constraint = constraint;
        this.params = Map.copyOf(params);
    }

    public String getColumn() {
        return column;
    }

    public MemberImportConstraint getConstraint() {
        return constraint;
    }

    public Map<String, String> getParams() {
        return params;
    }
}
