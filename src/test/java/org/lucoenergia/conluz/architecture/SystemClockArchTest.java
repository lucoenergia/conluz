package org.lucoenergia.conluz.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lucoenergia.conluz.architecture.systemclock.fixture.AllowedTimeReads;
import org.lucoenergia.conluz.architecture.systemclock.fixture.ConstructsDateOfNow;
import org.lucoenergia.conluz.architecture.systemclock.fixture.ConstructsGregorianCalendarOfNow;
import org.lucoenergia.conluz.architecture.systemclock.fixture.ReadsCalendarInstance;
import org.lucoenergia.conluz.architecture.systemclock.fixture.ReadsCurrentTimeMillis;
import org.lucoenergia.conluz.architecture.systemclock.fixture.ReadsJavaTimeNow;
import org.lucoenergia.conluz.architecture.systemclock.fixture.ReadsJavaTimeNowInAZone;
import org.lucoenergia.conluz.architecture.systemclock.fixture.ReadsSystemClock;
import org.lucoenergia.conluz.infrastructure.shared.time.ClockConfiguration;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Architecture test enforcing that production code reads the current time only from the injected
 * {@link java.time.Clock} bean (#355).
 *
 * <p>A value read from the JVM clock and one read from the bean cannot be compared safely: in tests the bean is a
 * clock that only moves when the test moves it, so the two differ by months, and in production a comparison would
 * mix two sources. Calls are matched by owner, name and exact parameter types, so the overloads that take a
 * {@code Clock}, {@code new Date(long)} and {@code System.nanoTime()}, which measures durations rather than reading
 * the wall clock, stay allowed. The only class that may read the JVM clock is the one declaring the bean.</p>
 */
public class SystemClockArchTest extends BaseArchTest {

    static final String MESSAGE = "Read the current time from the injected java.time.Clock bean, not from the JVM clock";

    private static final List<String> JAVA_TIME_TYPES = List.of("Instant", "LocalDate", "LocalDateTime", "LocalTime",
            "OffsetDateTime", "OffsetTime", "ZonedDateTime", "YearMonth", "Year", "MonthDay");

    @Test
    void productionCodeReadsTheCurrentTimeOnlyFromTheClockBean() {
        systemClockRule(resideInAPackage(BASE_PACKAGE + "..")).check(IMPORTED_CLASSES);
    }

    static Stream<Arguments> fixturesReadingTheJvmClock() {
        return Stream.of(
                Arguments.of(ReadsJavaTimeNow.class, JAVA_TIME_TYPES.size()),
                Arguments.of(ReadsJavaTimeNowInAZone.class, JAVA_TIME_TYPES.size() - 1),
                Arguments.of(ReadsSystemClock.class, 3),
                Arguments.of(ConstructsDateOfNow.class, 1),
                Arguments.of(ConstructsGregorianCalendarOfNow.class, 1),
                Arguments.of(ReadsCalendarInstance.class, 5),
                Arguments.of(ReadsCurrentTimeMillis.class, 1)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixturesReadingTheJvmClock")
    void theRuleFails_forEveryReadOfTheJvmClock(Class<?> fixture, int reads) {
        ArchRule rule = systemClockRule(resideInAPackage(fixture.getPackageName()));
        JavaClasses classes = new ClassFileImporter().importClasses(fixture);

        EvaluationResult result = rule.evaluate(classes);
        assertEquals(reads, result.getFailureReport().getDetails().size(),
                () -> String.join("\n", result.getFailureReport().getDetails()));

        AssertionError error = assertThrows(AssertionError.class, () -> rule.check(classes));
        assertTrue(error.getMessage().contains(MESSAGE), error::getMessage);
    }

    @Test
    void theRulePasses_forTheReadsThatDoNotTouchTheJvmClock() {
        EvaluationResult result = systemClockRule(resideInAPackage(AllowedTimeReads.class.getPackageName()))
                .evaluate(new ClassFileImporter().importClasses(AllowedTimeReads.class));

        assertFalse(result.hasViolation(), () -> String.join("\n", result.getFailureReport().getDetails()));
    }

    static ArchRule systemClockRule(DescribedPredicate<? super JavaClass> scope) {
        return noClasses()
                .that(scope)
                .and().doNotHaveFullyQualifiedName(ClockConfiguration.class.getName())
                .should().callMethodWhere(readsTheJvmClock())
                .orShould().callConstructorWhere(constructsATimeOfNow())
                .as(MESSAGE);
    }

    private static DescribedPredicate<JavaMethodCall> readsTheJvmClock() {
        return DescribedPredicate.describe("a read of the JVM clock", call -> {
            String owner = call.getTargetOwner().getFullName();
            String name = call.getName();
            List<String> parameters = call.getTarget().getRawParameterTypes().stream().map(JavaClass::getFullName)
                    .toList();
            if (owner.startsWith("java.time.") && JAVA_TIME_TYPES.contains(owner.substring("java.time.".length()))
                    && name.equals("now")) {
                return parameters.isEmpty() || parameters.equals(List.of("java.time.ZoneId"));
            }
            if (owner.equals("java.time.Clock")) {
                return (name.equals("systemUTC") || name.equals("systemDefaultZone")) && parameters.isEmpty()
                        || name.equals("system") && parameters.equals(List.of("java.time.ZoneId"));
            }
            if (Set.of("java.util.Calendar", "java.util.GregorianCalendar").contains(owner)) {
                return name.equals("getInstance");
            }
            return owner.equals("java.lang.System") && name.equals("currentTimeMillis") && parameters.isEmpty();
        });
    }

    private static DescribedPredicate<JavaConstructorCall> constructsATimeOfNow() {
        return DescribedPredicate.describe("a construction of the current time", call -> {
            String owner = call.getTargetOwner().getFullName();
            return (owner.equals("java.util.Date") || owner.equals("java.util.GregorianCalendar"))
                    && call.getTarget().getRawParameterTypes().isEmpty();
        });
    }
}
