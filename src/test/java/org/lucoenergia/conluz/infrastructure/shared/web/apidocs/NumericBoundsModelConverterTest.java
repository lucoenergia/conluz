package org.lucoenergia.conluz.infrastructure.shared.web.apidocs;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.providers.ObjectMapperProvider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bounds a request body's numeric constraints publish in the contract, read from the schemas
 * swagger-core resolves with the converter registered, over classes that exist only for this test.
 */
class NumericBoundsModelConverterTest {

    // --- one case per supported annotation ---

    @Test
    @DisplayName("API-001 @Min publishes an inclusive minimum")
    void minPublishesAnInclusiveMinimum() {
        Schema<?> value = property(WithMin.class, "value");

        assertBounds(value, "5", null, null, null);
    }

    @Test
    @DisplayName("API-001 @Max publishes an inclusive maximum")
    void maxPublishesAnInclusiveMaximum() {
        Schema<?> value = property(WithMax.class, "value");

        assertBounds(value, null, null, "9", null);
    }

    @Test
    @DisplayName("API-001 an inclusive @DecimalMin publishes an inclusive minimum")
    void inclusiveDecimalMinPublishesAnInclusiveMinimum() {
        Schema<?> value = property(WithInclusiveDecimalMin.class, "value");

        assertBounds(value, "1.5", null, null, null);
    }

    @Test
    @DisplayName("API-001 an exclusive @DecimalMin publishes an exclusive minimum and no minimum")
    void exclusiveDecimalMinPublishesAnExclusiveMinimum() {
        Schema<?> value = property(WithExclusiveDecimalMin.class, "value");

        assertBounds(value, null, "1.5", null, null);
    }

    @Test
    @DisplayName("API-001 an inclusive @DecimalMax publishes an inclusive maximum")
    void inclusiveDecimalMaxPublishesAnInclusiveMaximum() {
        Schema<?> value = property(WithInclusiveDecimalMax.class, "value");

        assertBounds(value, null, null, "7.25", null);
    }

    @Test
    @DisplayName("API-001 an exclusive @DecimalMax publishes an exclusive maximum and no maximum")
    void exclusiveDecimalMaxPublishesAnExclusiveMaximum() {
        Schema<?> value = property(WithExclusiveDecimalMax.class, "value");

        assertBounds(value, null, null, null, "7.25");
    }

    @Test
    @DisplayName("API-001 @Positive publishes an exclusive minimum of zero")
    void positivePublishesAnExclusiveMinimumOfZero() {
        Schema<?> value = property(WithPositive.class, "value");

        assertBounds(value, null, "0", null, null);
    }

    @Test
    @DisplayName("API-001 @PositiveOrZero publishes a minimum of zero")
    void positiveOrZeroPublishesAMinimumOfZero() {
        Schema<?> value = property(WithPositiveOrZero.class, "value");

        assertBounds(value, "0", null, null, null);
    }

    @Test
    @DisplayName("API-001 @Negative publishes an exclusive maximum of zero")
    void negativePublishesAnExclusiveMaximumOfZero() {
        Schema<?> value = property(WithNegative.class, "value");

        assertBounds(value, null, null, null, "0");
    }

    @Test
    @DisplayName("API-001 @NegativeOrZero publishes a maximum of zero")
    void negativeOrZeroPublishesAMaximumOfZero() {
        Schema<?> value = property(WithNegativeOrZero.class, "value");

        assertBounds(value, null, null, "0", null);
    }

    @Test
    @DisplayName("API-001 @Digits publishes the largest and smallest values it allows")
    void digitsPublishesTheLargestAndSmallestValues() {
        Schema<?> value = property(WithDigits.class, "value");

        assertBounds(value, "-999.99", null, "999.99", null);
    }

    // --- combining bounds ---

    @Test
    @DisplayName("API-001 of two bounds on the same side, only the strictest is published")
    void ofTwoBoundsOnOneSideTheStrictestIsPublished() {
        Schema<?> value = property(WithTwoLowerAndTwoUpperBounds.class, "value");

        assertBounds(value, null, "2", "8", null);
    }

    @Test
    @DisplayName("API-001 an exclusive and an inclusive bound with the same value publish the exclusive one")
    void anExclusiveBoundWinsOverAnInclusiveOneOfTheSameValue() {
        Schema<?> value = property(WithExclusiveAndInclusiveAtTheSameValue.class, "value");

        assertBounds(value, null, "0", null, "100");
    }

    @Test
    @DisplayName("API-001 a stricter exclusive lower bound drops the minimum @Digits allows")
    void aStricterExclusiveLowerBoundDropsTheDigitsMinimum() {
        Schema<?> value = property(WithExclusiveMinimumAndDigits.class, "value");

        assertBounds(value, null, "0", "9999999999.99", null);
    }

    // --- what the converter leaves alone ---

    @Test
    @DisplayName("API-001 a model without numeric constraints is resolved exactly as without the converter")
    void aModelWithoutNumericConstraintsIsUnchanged() throws Exception {
        Map<String, Schema> with = withConverter().readAll(new AnnotatedType(Unconstrained.class));
        Map<String, Schema> without = new ModelConverters(true).readAll(new AnnotatedType(Unconstrained.class));

        assertEquals(Json31.mapper().writeValueAsString(without), Json31.mapper().writeValueAsString(with));
    }

    @Test
    @DisplayName("API-001 a bound changes no other keyword of the property or the model")
    void aBoundChangesNothingElse() throws Exception {
        Schema<?> model = withConverter()
                .readAll(new AnnotatedType(WithDescribedPositive.class)).get("WithDescribedPositive");
        Schema<?> value = (Schema<?>) model.getProperties().get("value");

        assertEquals("A described amount.", value.getDescription());
        assertEquals(new BigDecimal("12.5"), new BigDecimal(value.getExample().toString()));
        assertTrue(value.getTypes().contains("number"));
        assertEquals(List.of("value"), model.getRequired());
        assertBounds(value, null, "0", null, null);
    }

    // --- property names and model structure ---

    @Test
    @DisplayName("API-001 a property renamed with @JsonProperty gets its bounds under its JSON name")
    void aRenamedPropertyGetsItsBoundsUnderItsJsonName() {
        Schema<?> value = property(WithRenamedProperty.class, "amountEur");

        assertBounds(value, null, "0", null, null);
    }

    @Test
    @DisplayName("API-001 a constrained property that is not in the schema makes resolution fail")
    void aConstrainedPropertyMissingFromTheSchemaFails() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> withConverter().readAll(new AnnotatedType(WithHiddenConstrainedProperty.class)));

        assertTrue(failure.getMessage().contains(WithHiddenConstrainedProperty.class.getName() + ".value"));
    }

    @Test
    @DisplayName("API-001 a constrained property Jackson ignores is not an error")
    void aConstrainedPropertyJacksonIgnoresIsNotAnError() {
        Map<String, Schema> models = withConverter()
                .readAll(new AnnotatedType(WithIgnoredConstrainedProperty.class));

        Map<String, Schema> properties = models.get("WithIgnoredConstrainedProperty").getProperties();
        assertFalse(properties.containsKey("value"));
        assertTrue(properties.containsKey("name"));
    }

    @Test
    @DisplayName("API-001 a constrained type used as a field of another type gets its bounds in its own model")
    void aConstrainedTypeUsedAsAFieldGetsItsBounds() {
        Map<String, Schema> models = withConverter()
                .readAll(new AnnotatedType(WithNestedField.class));

        assertBounds(propertyOf(models, "Nested", "value"), null, "0", "99.9", null);
    }

    @Test
    @DisplayName("API-001 a constrained type used as an array item gets its bounds in its own model")
    void aConstrainedTypeUsedAsAnArrayItemGetsItsBounds() {
        Map<String, Schema> models = withConverter()
                .readAll(new AnnotatedType(WithNestedList.class));

        assertBounds(propertyOf(models, "Nested", "value"), null, "0", "99.9", null);
    }

    // --- helpers ---

    private static ModelConverters withConverter() {
        ModelConverters converters = new ModelConverters(true);
        converters.addConverter(new NumericBoundsModelConverter(new ObjectMapperProvider(new SpringDocConfigProperties())));
        return converters;
    }

    private static Schema<?> property(Class<?> model, String name) {
        return propertyOf(withConverter().readAll(new AnnotatedType(model)), model.getSimpleName(), name);
    }

    private static Schema<?> propertyOf(
            Map<String, Schema> models, String model, String name) {
        Schema<?> property =
                (Schema<?>) models.get(model).getProperties().get(name);
        assertTrue(property != null, model + "." + name + " is not in the schema");
        return property;
    }

    /**
     * Asserts every bound keyword of the property at once, so that a side not mentioned is asserted
     * absent rather than ignored. The OpenAPI 3.0 boolean flags must never be left behind.
     */
    private static void assertBounds(Schema<?> property, String minimum,
                                     String exclusiveMinimum, String maximum, String exclusiveMaximum) {
        assertDecimal(minimum, property.getMinimum(), "minimum");
        assertDecimal(exclusiveMinimum, property.getExclusiveMinimumValue(), "exclusiveMinimum");
        assertDecimal(maximum, property.getMaximum(), "maximum");
        assertDecimal(exclusiveMaximum, property.getExclusiveMaximumValue(), "exclusiveMaximum");
        assertNull(property.getExclusiveMinimum(), "the 3.0 exclusiveMinimum flag");
        assertNull(property.getExclusiveMaximum(), "the 3.0 exclusiveMaximum flag");
    }

    private static void assertDecimal(String expected, BigDecimal actual, String keyword) {
        if (expected == null) {
            assertNull(actual, keyword);
        } else {
            assertTrue(actual != null && new BigDecimal(expected).compareTo(actual) == 0,
                    keyword + " is " + actual + ", expected " + expected);
        }
    }

    // --- test-only models ---

    static class WithMin {
        @Min(5)
        public Integer value;
    }

    static class WithMax {
        @Max(9)
        public Integer value;
    }

    static class WithInclusiveDecimalMin {
        @DecimalMin("1.5")
        public BigDecimal value;
    }

    static class WithExclusiveDecimalMin {
        @DecimalMin(value = "1.5", inclusive = false)
        public BigDecimal value;
    }

    static class WithInclusiveDecimalMax {
        @DecimalMax("7.25")
        public BigDecimal value;
    }

    static class WithExclusiveDecimalMax {
        @DecimalMax(value = "7.25", inclusive = false)
        public BigDecimal value;
    }

    static class WithPositive {
        @Positive
        public Double value;
    }

    static class WithPositiveOrZero {
        @PositiveOrZero
        public BigDecimal value;
    }

    static class WithNegative {
        @Negative
        public BigDecimal value;
    }

    static class WithNegativeOrZero {
        @NegativeOrZero
        public Integer value;
    }

    static class WithDigits {
        @Digits(integer = 3, fraction = 2)
        public BigDecimal value;
    }

    static class WithTwoLowerAndTwoUpperBounds {
        @Min(1)
        @DecimalMin(value = "2", inclusive = false)
        @Max(10)
        @DecimalMax("8")
        public BigDecimal value;
    }

    static class WithExclusiveAndInclusiveAtTheSameValue {
        @PositiveOrZero
        @Positive
        @Max(100)
        @DecimalMax(value = "100", inclusive = false)
        public BigDecimal value;
    }

    static class WithExclusiveMinimumAndDigits {
        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 10, fraction = 2)
        public BigDecimal value;
    }

    static class Unconstrained {
        @NotNull
        @io.swagger.v3.oas.annotations.media.Schema(description = "An amount without bounds.", example = "3.5")
        public BigDecimal value;
        public String name;
    }

    @io.swagger.v3.oas.annotations.media.Schema(requiredProperties = {"value"})
    static class WithDescribedPositive {
        @Positive
        @io.swagger.v3.oas.annotations.media.Schema(description = "A described amount.", example = "12.5")
        public BigDecimal value;
    }

    static class WithRenamedProperty {
        @Positive
        @JsonProperty("amountEur")
        public BigDecimal value;
    }

    static class WithHiddenConstrainedProperty {
        @Positive
        @io.swagger.v3.oas.annotations.media.Schema(hidden = true)
        public BigDecimal value;
        public String name;
    }

    static class WithIgnoredConstrainedProperty {
        @Positive
        @JsonIgnore
        public BigDecimal value;
        public String name;
    }

    static class Nested {
        @Positive
        @DecimalMax("99.9")
        public BigDecimal value;
    }

    static class WithNestedField {
        public Nested nested;
    }

    static class WithNestedList {
        public List<Nested> items;
    }
}
