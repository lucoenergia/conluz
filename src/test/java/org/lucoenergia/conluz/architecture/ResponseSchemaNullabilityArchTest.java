package org.lucoenergia.conluz.architecture;

import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Architecture test enforcing that response DTOs document, per field, whether the JSON key is
 * always present ({@code required}) and whether its value may be {@code null} (nullable).
 *
 * <p>Jackson's default null-inclusion is {@code ALWAYS} in this application (no
 * {@code spring.jackson.default-property-inclusion}, no {@code @JsonInclude}, no custom
 * {@code ObjectMapper} bean) -- verified by hitting a live endpoint and observing a null field
 * serialize as an explicit {@code "field": null}, not an absent key. This means the JSON key for
 * every response field is always present, so every field is {@code required}; nullability is a
 * fully independent second axis. Declaring a field required when it can be null is worse than
 * leaving it undeclared: it produces a client type that lies (no compiler warning, a crash instead
 * of a handled empty case), so this test does not push toward marking fields required by default --
 * it only asserts that a deliberate choice was made for every field.</p>
 *
 * <p>It also actively rejects {@code @Schema(nullable = true)}: springdoc/swagger-core 2.2.30
 * generates OpenAPI 3.1, under which {@code nullable} is silently dropped (verified: it never
 * appears anywhere in the generated document, even on fields that carry it). The replacement is
 * {@code @Schema(types = {"<T>", "null"})}, which does render correctly as
 * {@code "type": ["<T>", "null"]}.</p>
 */
public class ResponseSchemaNullabilityArchTest extends BaseArchTest {

    @Test
    void responseFieldsDeclareRequiredAndNullabilityDeliberately() {
        classes()
                .that().resideInAPackage("..infrastructure..")
                .and().haveSimpleNameEndingWith("Response")
                .should(ResponseSchemaNullabilityCondition.declareRequiredAndNullabilityDeliberately())
                .check(IMPORTED_CLASSES);
    }
}
