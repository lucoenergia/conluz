package org.lucoenergia.conluz.infrastructure.shared.web.apidocs;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.Length;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.providers.ObjectMapperProvider;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lengths a request body's string constraints publish in the contract, read from the schemas swagger-core
 * resolves with the converter registered, over classes that exist only for this test.
 */
class StringLengthModelConverterTest {

    @Test
    @DisplayName("API-003 @Size publishes its maximum as maxLength and no minLength")
    void sizePublishesItsMaximumAndNoMinLength() {
        Schema<?> value = property(WithSize.class, "value");

        assertEquals(250, value.getMaxLength());
        assertNull(value.getMinLength());
    }

    @Test
    @DisplayName("API-003 @Size next to @NotBlank keeps the minLength of 1 that @NotBlank publishes")
    void sizeNextToNotBlankKeepsMinLengthOne() {
        Schema<?> value = property(WithSizeAndNotBlank.class, "value");

        assertEquals(250, value.getMaxLength());
        assertEquals(1, value.getMinLength());
    }

    @Test
    @DisplayName("API-003 @Size next to @NotEmpty keeps the minLength of 1 that @NotEmpty publishes")
    void sizeNextToNotEmptyKeepsMinLengthOne() {
        Schema<?> value = property(WithSizeAndNotEmpty.class, "value");

        assertEquals(250, value.getMaxLength());
        assertEquals(1, value.getMinLength());
    }

    @Test
    @DisplayName("API-003 a @Size minimum stricter than @NotBlank is published as minLength")
    void aStricterSizeMinimumIsPublished() {
        Schema<?> value = property(WithSizeMinAndNotBlank.class, "value");

        assertEquals(10, value.getMaxLength());
        assertEquals(3, value.getMinLength());
    }

    @Test
    @DisplayName("API-003 a property without @Size is resolved as without the converter")
    void aPropertyWithoutSizeIsResolvedAsWithoutTheConverter() {
        Map<String, Schema> with = withConverter().readAll(new AnnotatedType(WithoutSize.class));
        Map<String, Schema> without = new ModelConverters(true).readAll(new AnnotatedType(WithoutSize.class));

        assertEquals(without, with);
        assertEquals(1, propertyOf(with, "WithoutSize", "notBlank").getMinLength());
        assertNull(propertyOf(with, "WithoutSize", "length").getMaxLength());
    }

    // --- helpers ---

    private static ModelConverters withConverter() {
        ModelConverters converters = new ModelConverters(true);
        converters.addConverter(
                new StringLengthModelConverter(new ObjectMapperProvider(new SpringDocConfigProperties())));
        return converters;
    }

    private static Schema<?> property(Class<?> model, String name) {
        return propertyOf(withConverter().readAll(new AnnotatedType(model)), model.getSimpleName(), name);
    }

    private static Schema<?> propertyOf(Map<String, Schema> models, String model, String name) {
        Schema<?> property = (Schema<?>) models.get(model).getProperties().get(name);
        assertTrue(property != null, model + "." + name + " is not in the schema");
        return property;
    }

    // --- test-only models ---

    static class WithSize {
        @Size(max = 250)
        public String value;
    }

    static class WithSizeAndNotBlank {
        @NotBlank
        @Size(max = 250)
        public String value;
    }

    static class WithSizeAndNotEmpty {
        @NotEmpty
        @Size(max = 250)
        public String value;
    }

    static class WithSizeMinAndNotBlank {
        @NotBlank
        @Size(min = 3, max = 10)
        public String value;
    }

    static class WithoutSize {
        @NotBlank
        public String notBlank;
        @Length(min = 1)
        public String length;
    }
}
