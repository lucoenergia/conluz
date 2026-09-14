package org.lucoenergia.conluz.architecture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

/**
 * Shared ArchUnit condition used by {@link SchemaExampleArchTest}. See that class for the full
 * rationale.
 */
final class SchemaExampleCondition {

    private static final Set<String> STRING_LIKE_TYPES = Set.of(
            "java.lang.String", "java.util.UUID", "java.time.Instant",
            "java.time.LocalDate", "java.time.LocalDateTime");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SchemaExampleCondition() {
    }

    static ArchCondition<JavaClass> notCorruptStringExamples() {
        return new ArchCondition<>("not carry a @Schema(example = \"...\") on a string-like field " +
                "whose value springdoc resolves to a non-string JSON node") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaField field : javaClass.getFields()) {
                    if (!field.isAnnotatedWith(Schema.class) || !isStringLike(field)) {
                        continue;
                    }
                    String example = field.getAnnotationOfType(Schema.class).example();
                    if (example.isEmpty()) {
                        continue;
                    }
                    resolvedNonStringType(example).ifPresent(renderedAs -> events.add(SimpleConditionEvent.violated(
                            field, message(javaClass, field, example, renderedAs))));
                }
            }
        };
    }

    private static boolean isStringLike(JavaField field) {
        JavaClass rawType = field.getRawType();
        return STRING_LIKE_TYPES.contains(rawType.getName()) || rawType.isEnum();
    }

    /**
     * Mirrors springdoc's own example resolution: it feeds the example string through Jackson's
     * {@code readTree}, which (with {@code FAIL_ON_TRAILING_TOKENS} off by default) happily parses
     * a leading valid JSON scalar and ignores well-formed trailing content -- e.g. "2024 winter
     * distribution" parses as the number 2024. A malformed leading token (e.g. "2024-01-15", where
     * the hyphen breaks the number) throws, and springdoc falls back to the raw string -- safe, no
     * violation.
     */
    private static java.util.Optional<String> resolvedNonStringType(String example) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(example);
            if (node != null && !node.isTextual()) {
                return java.util.Optional.of(node.getNodeType().name());
            }
        } catch (Exception e) {
            // Malformed leading token: springdoc falls back to the raw string. Safe.
        }
        return java.util.Optional.empty();
    }

    private static String message(JavaClass javaClass, JavaField field, String example, String renderedAs) {
        return String.format(
                "Field %s.%s has @Schema(example = \"%s\"), but its leading characters parse as a " +
                        "complete JSON %s -- springdoc will render it as a %s, not a string, in the " +
                        "generated OpenAPI document (e.g. \"2024 winter distribution\" renders as the " +
                        "number 2024). Reword the example so it doesn't start with a bare scalar token " +
                        "followed by whitespace or end-of-string.",
                javaClass.getSimpleName(), field.getName(), example, renderedAs.toLowerCase(), renderedAs.toLowerCase());
    }
}
