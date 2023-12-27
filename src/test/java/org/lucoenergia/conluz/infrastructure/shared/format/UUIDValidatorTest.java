package org.lucoenergia.conluz.infrastructure.shared.format;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class UUIDValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "268f3820-dc89-4505-b128-5189dd032e2c",
            "39266ac6-a4fd-11ee-a506-0242ac120002",
            "018cad23-20ef-78e7-b0f7-c0d3794275f8"
    })
    void testValidUUID(String value) {
        Assertions.assertTrue(UUIDValidator.validate(value));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            " ",
            "invalid-value"
    })
    void testInvalidUUID(String value) {
        Assertions.assertFalse(UUIDValidator.validate(value));
    }
}
