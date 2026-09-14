package org.lucoenergia.conluz.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Shared ArchUnit condition used by {@link ResponseSchemaNullabilityArchTest} to enforce that every
 * field on a {@code *Response} class carries an explicit, deliberate marker for whether it is
 * required (the JSON key is always present) and whether it is nullable (the value may be
 * {@code null}). See {@link ResponseSchemaNullabilityArchTest} for the full rationale.
 */
final class ResponseSchemaNullabilityCondition {

    private ResponseSchemaNullabilityCondition() {
    }

    static ArchCondition<JavaClass> declareRequiredAndNullabilityDeliberately() {
        return new ArchCondition<>("declare every field as required (via @Schema(requiredProperties " +
                "= {...}) or field-level @Schema(requiredMode = REQUIRED)) and, if it can be null, " +
                "as nullable via @Schema(types = {\"<T>\", \"null\"}) -- never @Schema(nullable = true)") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                Set<String> requiredProperties = classRequiredProperties(javaClass);

                for (JavaField field : javaClass.getFields()) {
                    if (!field.isAnnotatedWith(Schema.class)) {
                        if (!requiredProperties.contains(field.getName())) {
                            events.add(SimpleConditionEvent.violated(field, notDeclaredMessage(javaClass, field)));
                        }
                        continue;
                    }

                    Schema fieldSchema = field.getAnnotationOfType(Schema.class);

                    if (fieldSchema.nullable()) {
                        events.add(SimpleConditionEvent.violated(field, nullableAttributeMessage(javaClass, field)));
                        continue;
                    }

                    boolean isRequired = requiredProperties.contains(field.getName())
                            || fieldSchema.requiredMode() == Schema.RequiredMode.REQUIRED;
                    boolean isNullable = Arrays.asList(fieldSchema.types()).contains("null");

                    if (!isRequired && !isNullable) {
                        events.add(SimpleConditionEvent.violated(field, notDeclaredMessage(javaClass, field)));
                    }
                }
            }
        };
    }

    private static Set<String> classRequiredProperties(JavaClass javaClass) {
        if (!javaClass.isAnnotatedWith(Schema.class)) {
            return Set.of();
        }
        return new HashSet<>(Arrays.asList(javaClass.getAnnotationOfType(Schema.class).requiredProperties()));
    }

    private static String notDeclaredMessage(JavaClass javaClass, JavaField field) {
        return String.format(
                "Field %s.%s has no explicit required/nullable marker. Add \"%s\" to the class's " +
                        "@Schema(requiredProperties = {...}) if it is always populated, or annotate the " +
                        "field @Schema(types = {\"<T>\", \"null\"}) if it can legitimately be null. " +
                        "See AGENTS.md 'Response schema annotations'.",
                javaClass.getSimpleName(), field.getName(), field.getName());
    }

    private static String nullableAttributeMessage(JavaClass javaClass, JavaField field) {
        return String.format(
                "Field %s.%s uses @Schema(nullable = true), which springdoc silently drops under " +
                        "OpenAPI 3.1 (verified: it never appears in the generated document, even for " +
                        "fields that carry it today). Use @Schema(types = {\"<T>\", \"null\"}) instead, " +
                        "e.g. types = {\"string\", \"null\"}. See AGENTS.md 'Response schema annotations'.",
                javaClass.getSimpleName(), field.getName());
    }
}
