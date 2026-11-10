package org.lucoenergia.conluz.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.architecture.testcontext.fixture.DeclaresDynamicPropertySource;
import org.lucoenergia.conluz.architecture.testcontext.fixture.DeclaresMockitoBean;
import org.lucoenergia.conluz.architecture.testcontext.fixture.DeclaresRandomPort;
import org.lucoenergia.conluz.architecture.testcontext.fixture.DeclaresTestPropertySource;
import org.lucoenergia.conluz.architecture.testcontext.fixture.UsesTheSharedConfiguration;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Architecture test keeping the number of Spring test contexts down.
 *
 * <p>Spring starts a context for every distinct test configuration, and every Gradle fork starts its own copy. A test
 * class that declares a {@code @MockitoBean} or {@code @MockitoSpyBean} field, a {@code @DynamicPropertySource}
 * method, or a class annotation such as {@code @TestPropertySource}, has a configuration of its own: its first test
 * waits for a new context, and the context keeps a connection pool and heap for the rest of the run. A bean a test
 * replaces is declared once, as a spy, on {@link BaseIntegrationTest}, and stubbed by the test that needs it.</p>
 *
 * <p>Only the classes in {@link #ALLOWED} declare a configuration of their own, each with the reason a shared spy
 * cannot replace it: all of them set properties read once, at startup, or start a real server.</p>
 */
public class TestContextConfigurationArchTest {

    static final String MESSAGE = "Integration tests must not declare their own Spring test configuration; stub a "
            + "spy declared on BaseIntegrationTest instead, or add the class to ALLOWED with the reason";

    private static final String BASE = "org.lucoenergia.conluz.infrastructure.";
    private static final String MAIL_PROPERTIES = "mail properties are bound once, at startup";

    /**
     * The classes allowed a configuration of their own, with the reason.
     */
    static final Map<String, String> ALLOWED = Map.ofEntries(
            Map.entry(BaseIntegrationTest.class.getName(), "the shared configuration"),
            Map.entry(BASE + "admin.community.membership.register.BaseRegisterMemberTest",
                    "sends to an embedded SMTP server: " + MAIL_PROPERTIES),
            Map.entry(BASE + "admin.community.membership.register.BaseSendingMemberTest",
                    "turns sending on, with a public web URL: " + MAIL_PROPERTIES),
            Map.entry(BASE + "admin.community.membership.invitation.SendInvitationWithEmailDisabledControllerTest",
                    "tests the refusal with sending off: " + MAIL_PROPERTIES),
            Map.entry(BASE + "admin.community.membership.invitation.SendInvitationWithoutPublicUrlControllerTest",
                    "tests the refusal without a public web URL: " + MAIL_PROPERTIES),
            Map.entry(BASE + "admin.user.password.reset.BasePasswordResetTest",
                    "sends to an embedded SMTP server: " + MAIL_PROPERTIES),
            Map.entry(BASE + "admin.user.password.reset.RequestPasswordResetWithoutPublicUrlControllerTest",
                    "tests a reset request without a public web URL: " + MAIL_PROPERTIES),
            Map.entry(BASE + "shared.email.EmailHealthTest",
                    "tests the health of an unreachable SMTP server: " + MAIL_PROPERTIES),
            Map.entry(BASE + "shared.email.EmailSendingEnabledTest",
                    "tests sending with credentials and a sender name: " + MAIL_PROPERTIES),
            Map.entry(BASE + "admin.operator.GetOperatorContactFullyConfiguredControllerTest",
                    "tests the operator contact properties, bound once, at startup"),
            Map.entry(BASE + "admin.supply.tariff.EstimatedPriceConfigurationTest",
                    "tests the estimated tariff properties, bound once, at startup"),
            Map.entry(BASE + "shared.web.clientaddress.BaseClientAddressTest",
                    "needs a real embedded Tomcat: RemoteIpValve does not run under MockMvc"),
            Map.entry(BASE + "shared.web.clientaddress.TrustedProxyClientAddressTest",
                    "configures Tomcat's trusted proxies, read once, at startup"),
            Map.entry(BASE + "shared.web.clientaddress.UntrustedPeerClientAddressTest",
                    "configures Tomcat's trusted proxies, read once, at startup"),
            Map.entry(BASE + "shared.web.error.PayloadTooLargeControllerTest",
                    "needs a real embedded Tomcat, which enforces the request size limit"));

    private static final String FIXTURES = "org.lucoenergia.conluz.architecture.testcontext.fixture";

    private static final JavaClasses TEST_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
            .withImportOption(location -> !location.contains(FIXTURES.replace('.', '/')))
            .importPackages(BaseArchTest.BASE_PACKAGE);

    private static final List<Class<? extends java.lang.annotation.Annotation>> CLASS_ANNOTATIONS = List.of(
            TestPropertySource.class, DirtiesContext.class, ContextConfiguration.class, Import.class,
            ActiveProfiles.class, AutoConfigureMockMvc.class);

    private static final List<String> SPRING_BOOT_TEST_ATTRIBUTES = List.of(
            "webEnvironment", "properties", "classes", "args", "value", "useMainMethod");

    @Test
    void integrationTestsShareTheTestConfiguration() {
        rule(ALLOWED.keySet()).check(TEST_CLASSES);
    }

    @Test
    void everyAllowedClassStillDeclaresAConfigurationOfItsOwn() {
        for (String allowed : ALLOWED.keySet()) {
            JavaClass javaClass = TEST_CLASSES.get(allowed);
            assertNotNull(javaClass, allowed);
            assertFalse(declarations(javaClass).isEmpty(),
                    () -> allowed + " shares the test configuration now: take it out of ALLOWED");
        }
    }

    @Test
    void theRuleFails_forEachKindOfDeclaration() {
        ArchRule rule = rule(Set.of());
        JavaClasses fixtures = new ClassFileImporter().importClasses(DeclaresMockitoBean.class,
                DeclaresDynamicPropertySource.class, DeclaresTestPropertySource.class, DeclaresRandomPort.class,
                UsesTheSharedConfiguration.class);

        EvaluationResult result = rule.evaluate(fixtures);

        List<String> details = result.getFailureReport().getDetails();
        assertEquals(4, details.size(), () -> String.join("\n", details));
        assertTrue(details.stream().noneMatch(detail -> detail.contains(UsesTheSharedConfiguration.class.getName())),
                () -> String.join("\n", details));
        AssertionError error = assertThrows(AssertionError.class, () -> rule.check(fixtures));
        assertTrue(error.getMessage().contains(MESSAGE), error::getMessage);
    }

    @Test
    void theRulePasses_forAnAllowedClass() {
        JavaClasses fixtures = new ClassFileImporter().importClasses(DeclaresMockitoBean.class,
                UsesTheSharedConfiguration.class);

        EvaluationResult result = rule(Set.of(DeclaresMockitoBean.class.getName())).evaluate(fixtures);

        assertFalse(result.hasViolation(), () -> String.join("\n", result.getFailureReport().getDetails()));
    }

    static ArchRule rule(Set<String> allowed) {
        return classes()
                .that().areAssignableTo(BaseIntegrationTest.class)
                .and(DescribedPredicate.describe("are not allowed a configuration of their own",
                        javaClass -> !allowed.contains(javaClass.getName())))
                .should(new ArchCondition<>("not declare a test configuration of their own") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        for (String declaration : declarations(javaClass)) {
                            events.add(SimpleConditionEvent.violated(javaClass,
                                    javaClass.getName() + " declares " + declaration));
                        }
                    }
                })
                .as(MESSAGE);
    }

    /**
     * What {@code javaClass} itself declares that makes its test configuration differ from its superclass's.
     */
    static List<String> declarations(JavaClass javaClass) {
        List<String> declarations = new ArrayList<>();
        javaClass.getFields().stream()
                .filter(field -> field.isAnnotatedWith(MockitoBean.class) || field.isAnnotatedWith(MockitoSpyBean.class))
                .forEach(field -> declarations.add("a replaced bean, field " + field.getName()));
        javaClass.getMethods().stream()
                .filter(method -> method.isAnnotatedWith(DynamicPropertySource.class))
                .forEach(method -> declarations.add("dynamic properties, method " + method.getName()));
        for (Class<? extends java.lang.annotation.Annotation> annotation : CLASS_ANNOTATIONS) {
            if (javaClass.isAnnotatedWith(annotation)) {
                declarations.add("@" + annotation.getSimpleName());
            }
        }
        javaClass.tryGetAnnotationOfType(SpringBootTest.class.getName())
                .filter(TestContextConfigurationArchTest::setsAnAttribute)
                .ifPresent(annotation -> declarations.add("@SpringBootTest with attributes"));
        return declarations;
    }

    private static boolean setsAnAttribute(JavaAnnotation<JavaClass> annotation) {
        return SPRING_BOOT_TEST_ATTRIBUTES.stream().anyMatch(annotation::hasExplicitlyDeclaredProperty);
    }
}
