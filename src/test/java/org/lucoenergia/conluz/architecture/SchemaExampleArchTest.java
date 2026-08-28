package org.lucoenergia.conluz.architecture;

import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Architecture test guarding against a springdoc/swagger-core defect: an {@code @Schema(example =
 * "...")} string whose leading characters parse as a complete JSON scalar followed by whitespace or
 * end-of-string (e.g. {@code "2024 winter distribution"}) is silently rendered in the generated
 * OpenAPI document as that scalar (the bare number {@code 2024}), not as the intended string. A
 * malformed leading token (e.g. {@code "2024-01-15"}, where the hyphen breaks the number) is safe --
 * it throws during resolution and springdoc falls back to the raw string.
 *
 * <p>This was found independently three times in this codebase (two {@code *Body} examples, one
 * {@code *Response} example) and once more while writing this guard ({@code
 * SupplyDistributorResponse.code}). No automated test can detect a description that is merely
 * <em>wrong</em> -- prose that contradicts behaviour is only caught by review -- but this specific
 * class of defect (a type mismatch between the declared string schema and the rendered example) is
 * mechanically detectable, so it gets a guard instead of relying on the audit sticking.</p>
 */
public class SchemaExampleArchTest extends BaseArchTest {

    @Test
    void schemaExamplesOnStringLikeFieldsDoNotResolveToNonStringValues() {
        classes()
                .that().resideInAPackage("..infrastructure..")
                .should(SchemaExampleCondition.notCorruptStringExamples())
                .check(IMPORTED_CLASSES);
    }
}
