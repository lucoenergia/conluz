package org.lucoenergia.conluz.infrastructure.shared.format;

import java.util.regex.Pattern;

public final class UUIDValidator {

    private UUIDValidator() {
    }

    private static final Pattern UUID_REGEX =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    public static boolean validate(String value) {
        return value != null && UUID_REGEX.matcher(value).matches();
    }
}
