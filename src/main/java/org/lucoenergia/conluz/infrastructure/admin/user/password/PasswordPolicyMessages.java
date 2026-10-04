package org.lucoenergia.conluz.infrastructure.admin.user.password;

import org.lucoenergia.conluz.domain.admin.user.password.PasswordPolicy;
import org.lucoenergia.conluz.domain.admin.user.password.PasswordPolicyViolation;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Renders a {@link PasswordPolicyViolation} as a localised message, shared by the 400 response and by the per-row
 * errors of a user import so both say the same thing.
 */
@Component
public class PasswordPolicyMessages {

    private final MessageSource messageSource;

    public PasswordPolicyMessages(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String messageFor(PasswordPolicyViolation violation) {
        return switch (violation) {
            case TOO_SHORT -> message("error.user.password.policy.too.short", PasswordPolicy.MIN_CODE_POINTS);
            case TOO_LONG -> message("error.user.password.policy.too.long", PasswordPolicy.MAX_CODE_POINTS);
            case TOO_MANY_BYTES -> message("error.user.password.policy.too.many.bytes", PasswordPolicy.MAX_UTF8_BYTES);
        };
    }

    private String message(String key, int limit) {
        return messageSource.getMessage(key, new Object[]{limit}, LocaleContextHolder.getLocale());
    }
}
