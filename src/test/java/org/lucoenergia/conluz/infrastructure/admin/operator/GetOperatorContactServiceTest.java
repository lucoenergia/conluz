package org.lucoenergia.conluz.infrastructure.admin.operator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lucoenergia.conluz.domain.admin.operator.GetOperatorContactService;
import org.lucoenergia.conluz.domain.admin.operator.OperatorContact;
import org.lucoenergia.conluz.infrastructure.shared.contact.ContactEmailValidityImpl;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts only what binds and validates the operator contact, with the real {@code application.properties}, so the
 * settings are given exactly as an operator gives them: as {@code CONLUZ_OPERATOR_CONTACT_*} variables.
 */
class GetOperatorContactServiceTest {

    private static final String NAME = "CONLUZ_OPERATOR_CONTACT_NAME";
    private static final String EMAIL = "CONLUZ_OPERATOR_CONTACT_EMAIL";
    private static final String PHONE = "CONLUZ_OPERATOR_CONTACT_PHONE";
    private static final String SCHEDULE = "CONLUZ_OPERATOR_CONTACT_SCHEDULE";
    private static final String NOTES = "CONLUZ_OPERATOR_CONTACT_NOTES";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(GetOperatorContactServiceImpl.class, ContactEmailValidityImpl.class);

    @Test
    @DisplayName("CTC-005 with every variable blank the application starts with no operator contact")
    void everyVariableBlankStartsWithNoOperatorContact() {
        contextRunner.withPropertyValues(NAME + "= ", EMAIL + "=", PHONE + "=  ", SCHEDULE + "=", NOTES + "= ")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(GetOperatorContactService.class).getOperatorContact()).isEmpty();
                });
    }

    @Test
    @DisplayName("CTC-005 with no variable set the application starts with no operator contact")
    void noVariableSetStartsWithNoOperatorContact() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(GetOperatorContactService.class).getOperatorContact()).isEmpty();
        });
    }

    @Test
    @DisplayName("CTC-005 with name and email only the application starts, with no phone, schedule or notes")
    void nameAndEmailOnlyStartWithNoOptionalValues() {
        contextRunner.withPropertyValues(NAME + "=Lucoenergia", EMAIL + "=contact@example.org").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(GetOperatorContactService.class).getOperatorContact()).contains(
                    new OperatorContact("Lucoenergia", "contact@example.org", null, null, null));
        });
    }

    @Test
    @DisplayName("CTC-004 with every variable set the operator contact has all five values")
    void everyVariableSetGivesAllFiveValues() {
        contextRunner.withPropertyValues(NAME + "=Lucoenergia", EMAIL + "=contact@example.org",
                        PHONE + "=+34 600 000 000", SCHEDULE + "=Mornings", NOTES + "=Ask for support")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(GetOperatorContactService.class).getOperatorContact()).contains(
                            new OperatorContact("Lucoenergia", "contact@example.org", "+34 600 000 000",
                                    "Mornings", "Ask for support"));
                });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedConfigurations")
    @DisplayName("CTC-005 a partial or invalid operator contact fails startup naming the variable")
    void aPartialOrInvalidOperatorContactFailsStartupNamingTheVariable(String label, String[] expectedVariables,
                                                                       String[] properties) {
        contextRunner.withPropertyValues(properties).run(context -> {
            assertThat(context).hasFailed();
            assertThat(failureMessages(context)).contains(expectedVariables);
        });
    }

    private static Stream<Arguments> rejectedConfigurations() {
        return Stream.of(
                Arguments.of("CTC-005 only the email", new String[]{NAME},
                        new String[]{EMAIL + "=contact@example.org"}),
                Arguments.of("CTC-005 only the name", new String[]{EMAIL},
                        new String[]{NAME + "=Lucoenergia"}),
                Arguments.of("CTC-005 only the phone", new String[]{NAME, EMAIL},
                        new String[]{PHONE + "=+34 600 000 000"}),
                Arguments.of("CTC-005 the name and the optional values without an email", new String[]{EMAIL},
                        new String[]{NAME + "=Lucoenergia", PHONE + "=600000000", SCHEDULE + "=Mornings",
                                NOTES + "=Ask for support"}),
                Arguments.of("CTC-005 an invalid email", new String[]{EMAIL},
                        new String[]{NAME + "=Lucoenergia", EMAIL + "=not an email"}),
                Arguments.of("CTC-005 a phone with letters", new String[]{PHONE},
                        new String[]{NAME + "=Lucoenergia", EMAIL + "=contact@example.org",
                                PHONE + "=600 CALL ME"}),
                Arguments.of("CTC-005 a phone with fewer than 6 digits", new String[]{PHONE},
                        new String[]{NAME + "=Lucoenergia", EMAIL + "=contact@example.org", PHONE + "=+34 123"})
        );
    }

    private static String failureMessages(AssertableApplicationContext context) {
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = context.getStartupFailure(); cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append(System.lineSeparator());
        }
        return messages.toString();
    }
}
