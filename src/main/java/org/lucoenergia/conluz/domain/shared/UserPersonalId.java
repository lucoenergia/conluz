package org.lucoenergia.conluz.domain.shared;

import java.util.Locale;
import java.util.regex.Pattern;

public class UserPersonalId {

    /**
     * Characters removed from a personal ID wherever they appear: space, tab, line feed, carriage
     * return, form feed, vertical tab, no-break space (U+00A0), dot and hyphen. Removing the whitespace
     * anywhere also trims it. The set is explicit rather than {@code \s} so that the Liquibase changeset
     * {@code normalize_users_personal_id} can remove exactly the same characters in SQL.
     */
    private static final Pattern REMOVED_CHARACTERS = Pattern.compile("[ \\t\\n\\r\\f\\x0B\\u00A0.-]");

    private final String personalId;

    private UserPersonalId(String personalId) {
        this.personalId = personalId;
    }

    public static UserPersonalId of(String id) {
        return new UserPersonalId(id);
    }

    /**
     * The canonical form of a personal ID, used on every write and every lookup so that typing variants
     * of the same DNI/NIE/NIF ({@code 12345678a}, {@code 12.345.678-A}, {@code " 12345678 A "}) are one
     * identity: the characters above are removed and the rest is upper-cased. The format and the control
     * character are not validated.
     *
     * @return the normalised value, or {@code null} when {@code raw} is {@code null}
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        return REMOVED_CHARACTERS.matcher(raw).replaceAll("").toUpperCase(Locale.ROOT);
    }

    public String getPersonalId() {
        return personalId;
    }
}
