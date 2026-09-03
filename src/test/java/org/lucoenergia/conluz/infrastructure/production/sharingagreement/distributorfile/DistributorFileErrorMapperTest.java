package org.lucoenergia.conluz.infrastructure.production.sharingagreement.distributorfile;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.sharingagreement.distributorfile.DistributorFileError;
import org.lucoenergia.conluz.domain.production.sharingagreement.distributorfile.DistributorFileErrorCode;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorDetail;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.util.Locale;
import java.util.Map;

class DistributorFileErrorMapperTest {

    private static final MessageSource MESSAGE_SOURCE = buildMessageSource();

    private final DistributorFileErrorMapper mapper = new DistributorFileErrorMapper(MESSAGE_SOURCE);

    private static MessageSource buildMessageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:messages");
        messageSource.setDefaultEncoding("UTF-8");
        // Deterministic in CI/local runs regardless of the JVM's default locale: without this,
        // an English lookup on a host whose default locale is Spanish would silently resolve the
        // Spanish bundle instead of the root (English) one.
        messageSource.setFallbackToSystemLocale(false);
        return messageSource;
    }

    @Test
    void filenameShapeInvalidRendersEnglishMessageWithoutThrowing() {
        DistributorFileError error = new DistributorFileError(DistributorFileErrorCode.FILENAME_SHAPE_INVALID, null,
                Map.of("filename", "malformed_line_2023.txt"));

        RestErrorDetail detail = mapper.toRestErrorDetail(error, Locale.ENGLISH);

        Assertions.assertTrue(detail.getMessage().contains("malformed_line_2023.txt"));
        Assertions.assertTrue(detail.getMessage().contains("{code}_{YYYY}.txt"));
    }

    @Test
    void filenameShapeInvalidRendersSpanishMessageWithoutThrowing() {
        DistributorFileError error = new DistributorFileError(DistributorFileErrorCode.FILENAME_SHAPE_INVALID, null,
                Map.of("filename", "malformed_line_2023.txt"));

        RestErrorDetail detail = mapper.toRestErrorDetail(error, Locale.forLanguageTag("es"));

        Assertions.assertTrue(detail.getMessage().contains("malformed_line_2023.txt"));
        Assertions.assertTrue(detail.getMessage().contains("{codigo}_{AAAA}.txt"));
    }
}
