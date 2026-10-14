package org.lucoenergia.conluz.domain.admin.community.membership;

import org.lucoenergia.conluz.domain.shared.email.EmailAddresses;

import java.util.regex.Pattern;

/**
 * The data a community holds about one of its members, exactly as the community entered it (ADR-0003 §1).
 * Every value but the full name is optional, and an optional value that is empty or blank is recorded as
 * {@code null}: "not recorded" has one spelling. Nothing is trimmed.
 */
public record MemberRecord(String fullName, Integer memberNumber, String contactEmail, String address,
                           String phoneNumber) {

    /** The same characters {@link EmailAddresses#blankToNull} treats as blank. */
    private static final Pattern BLANK = Pattern.compile("[ \\t\\n\\r\\f\\x0B]*");

    public MemberRecord {
        contactEmail = EmailAddresses.blankToNull(contactEmail);
        address = blankToNull(address);
        phoneNumber = blankToNull(phoneNumber);
    }

    public boolean hasContactEmail() {
        return contactEmail != null;
    }

    private static String blankToNull(String value) {
        return value == null || BLANK.matcher(value).matches() ? null : value;
    }
}
