package org.lucoenergia.conluz.infrastructure.shared.personalid;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A personal ID that is not empty once normalised. A value made only of the characters normalisation removes
 * (whitespace, dots and hyphens) would otherwise be stored as the empty string. {@code null} is left to
 * {@code @NotBlank}.
 */
@Documented
@Constraint(validatedBy = NotEmptyOnceNormalisedValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotEmptyOnceNormalised {
    String message() default "must contain at least one character other than whitespace, dots and hyphens";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
