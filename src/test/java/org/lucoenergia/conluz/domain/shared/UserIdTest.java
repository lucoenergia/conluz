package org.lucoenergia.conluz.domain.shared;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserIdTest {

    @Test
    void sameUuid_isEqual_withTheSameHashCode() {
        UUID id = UUID.randomUUID();

        assertTrue(UserId.of(id).equals(UserId.of(id)));
        assertEquals(UserId.of(id).hashCode(), UserId.of(id).hashCode());
    }

    @Test
    void differentUuids_areNotEqual() {
        assertNotEquals(UserId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()));
    }

    @Test
    void isNotEqualToNull_orToAnotherType() {
        UUID id = UUID.randomUUID();

        assertFalse(UserId.of(id).equals(null));
        assertFalse(UserId.of(id).equals(id));
        assertFalse(UserId.of(id).equals(PlantId.of(id)));
    }

    @Test
    void equalityIsNullSafe() {
        assertEquals(UserId.of(null), UserId.of(null));
        assertEquals(UserId.of(null).hashCode(), UserId.of(null).hashCode());
        assertNotEquals(UserId.of(UUID.randomUUID()), UserId.of(null));
        assertNotEquals(UserId.of(null), UserId.of(UUID.randomUUID()));
    }
}
