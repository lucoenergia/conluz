package org.lucoenergia.conluz.infrastructure.shared.personalid;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;

public final class NotEmptyOnceNormalisedValidator implements ConstraintValidator<NotEmptyOnceNormalised, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || !UserPersonalId.normalize(value).isEmpty();
    }
}
