package org.lucoenergia.conluz.infrastructure.shared.format;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.UUID;
import java.util.regex.Pattern;

public final class UUIDValidator implements ConstraintValidator<ValidUUID, UUID> {

    public static final String PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private static final Pattern UUID_REGEX =
            Pattern.compile(String.format("^%s$", PATTERN));

    @Override
    public void initialize(ValidUUID constraintAnnotation) {
        // Initialization, if needed
    }

    @Override
    public boolean isValid(UUID value, ConstraintValidatorContext context) {
        return value != null && UUID_REGEX.matcher(value.toString()).matches();
    }

    public static boolean validate(String value) {
        return value != null && UUID_REGEX.matcher(value).matches();
    }
}
