package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.auth.TokenRejectionReason;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The exception for a rejected token can hold neither the token nor anything describing it (#347).
 */
class InvalidTokenExceptionTest {

    @Test
    void itHoldsOnlyTheReasonAndTheUserId() {
        Set<Class<?>> fieldTypes = Set.of(TokenRejectionReason.class, UUID.class);

        for (Field field : InvalidTokenException.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                Assertions.assertTrue(fieldTypes.contains(field.getType()), field::toString);
            }
        }
    }

    @Test
    void noConstructorAcceptsAStringOrACause() {
        for (Constructor<?> constructor : InvalidTokenException.class.getConstructors()) {
            Assertions.assertTrue(Arrays.stream(constructor.getParameterTypes())
                    .noneMatch(type -> type == String.class || Throwable.class.isAssignableFrom(type)),
                    constructor::toString);
        }
    }

    @Test
    void itsMessageIsTheReason_andItHasNoCause() {
        UUID userId = UUID.randomUUID();
        InvalidTokenException exception = new InvalidTokenException(TokenRejectionReason.USER_DISABLED, userId);

        Assertions.assertEquals("USER_DISABLED", exception.getMessage());
        Assertions.assertNull(exception.getCause());
        Assertions.assertEquals(TokenRejectionReason.USER_DISABLED, exception.getReason());
        Assertions.assertEquals(Optional.of(userId), exception.getUserId());
    }

    @Test
    void withoutAUserId_itReportsNone() {
        InvalidTokenException exception = new InvalidTokenException(TokenRejectionReason.MALFORMED);

        Assertions.assertTrue(exception.getUserId().isEmpty());
    }
}
