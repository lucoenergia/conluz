package org.lucoenergia.conluz.infrastructure.shared.web.error;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.Length;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.DirectFieldBindingResult;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BadRequestExceptionHandlerTest {

    @Test
    void anIllegalArgument_isAnswered400_butLoggedAtErrorWithItsStackTrace_asTheBrokenInvariantItIs() {
        MessageSource messageSource = mock(MessageSource.class);
        when(messageSource.getMessage(eq("error.invalid.arguments"), any(), any())).thenReturn("Invalid arguments.");
        BadRequestExceptionHandler handler = new BadRequestExceptionHandler(messageSource,
                new ErrorBuilder(messageSource, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)));

        try (LogCapture logs = LogCapture.ofRoot()) {
            ResponseEntity<RestError> response = handler.handleException(new IllegalArgumentException("Broken"));

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(logs.allOfThisThread()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getThrowableProxy()).isNotNull();
            });
        }
    }

    // --- maximum lengths of request bodies (#574) ---

    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    /** A value no response or log line may repeat. */
    private static final String SECRET = "SECRET-VALUE-0123456789";

    private BadRequestExceptionHandler realMessagesHandler;

    @BeforeEach
    void setUpRealMessages() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        realMessagesHandler = new BadRequestExceptionHandler(messageSource,
                new ErrorBuilder(messageSource, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)));
        LocaleContextHolder.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @AfterAll
    static void closeValidators() {
        VALIDATORS.close();
    }

    @Test
    @DisplayName("API-003 a value longer than its @Size is answered FIELD_TOO_LONG with its field, maximum and length")
    void aValueLongerThanItsSize_isAnsweredFieldTooLong() throws Exception {
        Lengths body = new Lengths();
        body.first = "a".repeat(6);

        RestError error = realMessagesHandler.handleException(invalid(body)).getBody();

        assertThat(error.getStatus()).isEqualTo(400);
        assertThat(error.getErrors()).singleElement().satisfies(detail -> {
            assertThat(detail.getCode()).isEqualTo(RestErrorCode.FIELD_TOO_LONG);
            assertThat(detail.getParams()).isEqualTo(Map.of("field", "first", "maxLength", "5", "n", "6"));
            assertThat(detail.getMessage()).isEqualTo("The field 'first' can have at most 5 characters. It has 6.");
        });
        assertThat(error.getMessage()).isEqualTo(error.getErrors().get(0).getMessage());
    }

    @Test
    @DisplayName("API-003 two fields longer than their @Size are answered with one detail each, each with its own maximum")
    void twoValuesLongerThanTheirSize_areAnsweredWithOneDetailEach() throws Exception {
        Lengths body = new Lengths();
        body.first = "a".repeat(7);
        body.second = "b".repeat(4);

        RestError error = realMessagesHandler.handleException(invalid(body)).getBody();

        assertThat(error.getErrors())
                .extracting(RestErrorDetail::getCode, RestErrorDetail::getParams)
                .containsExactly(
                        tuple(RestErrorCode.FIELD_TOO_LONG, Map.of("field", "first", "maxLength", "5", "n", "7")),
                        tuple(RestErrorCode.FIELD_TOO_LONG, Map.of("field", "second", "maxLength", "3", "n", "4")));
        assertThat(error.getMessage()).isEqualTo("2 fields have errors.");
    }

    @Test
    @DisplayName("API-003 next to a field that is too long, any other invalid field keeps its untyped detail")
    void nextToATooLongField_anotherInvalidFieldKeepsItsUntypedDetail() throws Exception {
        Lengths body = new Lengths();
        body.first = "a".repeat(6);
        body.name = " ";

        RestError error = realMessagesHandler.handleException(invalid(body)).getBody();

        assertThat(error.getErrors())
                .extracting(RestErrorDetail::getCode, RestErrorDetail::getParams)
                .containsExactly(
                        tuple(RestErrorCode.FIELD_TOO_LONG, Map.of("field", "first", "maxLength", "5", "n", "6")),
                        tuple(null, null));
        assertThat(error.getErrors().get(1).getMessage()).startsWith("Property 'name' is invalid.");
    }

    @Test
    @DisplayName("API-003 a @NotBlank failure keeps today's response: one untyped detail with every reason")
    void aNotBlankFailure_keepsTodaysResponse() throws Exception {
        Lengths body = new Lengths();
        body.name = " ";

        assertKeepsTodaysResponse(realMessagesHandler.handleException(invalid(body)).getBody(), "name");
    }

    @Test
    @DisplayName("API-003 a @Length(min) failure keeps today's response: one untyped detail with every reason")
    void aLengthMinFailure_keepsTodaysResponse() throws Exception {
        Lengths body = new Lengths();
        body.token = "";

        assertKeepsTodaysResponse(realMessagesHandler.handleException(invalid(body)).getBody(), "token");
    }

    @Test
    @DisplayName("API-003 a @Length(max) failure is not a published maximum and keeps today's response")
    void aLengthMaxFailure_keepsTodaysResponse() throws Exception {
        Lengths body = new Lengths();
        body.legacy = "c".repeat(5);

        assertKeepsTodaysResponse(realMessagesHandler.handleException(invalid(body)).getBody(), "legacy");
    }

    @Test
    @DisplayName("API-003 the message is in the language of the request")
    void theMessageIsInTheLanguageOfTheRequest() throws Exception {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("es"));
        Lengths body = new Lengths();
        body.first = "a".repeat(6);

        RestError error = realMessagesHandler.handleException(invalid(body)).getBody();

        assertThat(error.getErrors().get(0).getMessage())
                .isEqualTo("El campo 'first' puede tener hasta 5 caracteres. Ahora tiene 6.");
    }

    @Test
    @DisplayName("API-003 the rejected value is never repeated in the response or a log line")
    void theRejectedValueIsNeverRepeated() throws Exception {
        Lengths body = new Lengths();
        body.first = SECRET;
        body.second = SECRET;
        body.name = " ";

        try (LogCapture logs = LogCapture.ofRoot()) {
            ResponseEntity<RestError> response = realMessagesHandler.handleException(invalid(body));

            String json = JsonMapper.builder().findAndAddModules().build().writeValueAsString(response.getBody());
            assertThat(json).contains("FIELD_TOO_LONG").doesNotContain(SECRET);
            assertThat(logs.allOfThisThread()).isNotEmpty();
            assertThat(logs.allOfThisThread()).extracting(ILoggingEvent::getFormattedMessage)
                    .allSatisfy(line -> assertThat(line).doesNotContain(SECRET));
        }
    }

    private static void assertKeepsTodaysResponse(RestError error, String field) {
        assertThat(error.getStatus()).isEqualTo(400);
        assertThat(error.getErrors()).singleElement().satisfies(detail -> {
            assertThat(detail.getCode()).isNull();
            assertThat(detail.getParams()).isNull();
            assertThat(detail.getMessage()).isEqualTo(error.getMessage());
        });
        assertThat(error.getMessage())
                .startsWith("Some properties are invalid. Please check your request and try again. Errors found: ")
                .contains("Property '" + field + "' is invalid. Reason: ");
    }

    private static MethodArgumentNotValidException invalid(Object body) throws NoSuchMethodException {
        DirectFieldBindingResult result = new DirectFieldBindingResult(body, "body");
        new SpringValidatorAdapter(VALIDATORS.getValidator()).validate(body, result);
        assertThat(result.hasFieldErrors()).isTrue();
        MethodParameter parameter = new MethodParameter(
                BadRequestExceptionHandlerTest.class.getDeclaredMethod("invalid", Object.class), 0);
        return new MethodArgumentNotValidException(parameter, result);
    }

    /**
     * A request body that exists only for this test: two fields with different maximum lengths and the other
     * constraints whose response must not change.
     */
    static class Lengths {
        @Size(max = 5)
        String first = "a";
        @Size(max = 3)
        String second = "b";
        @NotBlank
        String name = "name";
        @Length(min = 1)
        String token = "t";
        @Length(max = 4)
        String legacy = "l";
    }
}
