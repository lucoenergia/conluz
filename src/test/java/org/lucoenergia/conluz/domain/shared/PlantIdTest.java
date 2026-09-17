package org.lucoenergia.conluz.domain.shared;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PlantIdTest {

    @Test
    void toString_doesNotThrow_whenTheIdIsNull() {
        // of(null) is legal, and PlantExceptionHandler renders this straight into a 404 body, so a
        // dereference here would turn a not-found into a 500.
        assertDoesNotThrow(() -> PlantId.of(null).toString());
        assertEquals("PlantId{id=null}", PlantId.of(null).toString());
    }

    @Test
    void toString_rendersTheId() {
        UUID id = UUID.randomUUID();
        assertEquals("PlantId{id=" + id + "}", PlantId.of(id).toString());
    }

    @Test
    void equalityIsByValueAndNullSafe() {
        UUID id = UUID.randomUUID();
        assertEquals(PlantId.of(id), PlantId.of(id));
        assertEquals(PlantId.of(null), PlantId.of(null));
        assertNotEquals(PlantId.of(id), PlantId.of(null));
        assertEquals(PlantId.of(id).hashCode(), PlantId.of(id).hashCode());
    }
}
