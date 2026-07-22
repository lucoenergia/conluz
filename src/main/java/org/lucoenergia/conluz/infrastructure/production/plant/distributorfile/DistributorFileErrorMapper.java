package org.lucoenergia.conluz.infrastructure.production.plant.distributorfile;

import org.lucoenergia.conluz.domain.production.plant.distributorfile.DistributorFileError;
import org.lucoenergia.conluz.domain.production.plant.distributorfile.DistributorFileErrorCode;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorCode;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorDetail;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps a domain {@link DistributorFileError} (no web-layer type) to the typed
 * {@link RestErrorDetail} contract. An explicit {@link EnumMap} pairing, rather than a naming
 * convention derived at runtime (e.g. {@code RestErrorCode.valueOf("DISTRIBUTOR_FILE_" + ...)}),
 * so a drift between {@link DistributorFileErrorCode} and {@link RestErrorCode} fails to compile
 * instead of failing at runtime.
 */
@Component
public class DistributorFileErrorMapper {

    private static final Map<DistributorFileErrorCode, RestErrorCode> CODES = new EnumMap<>(DistributorFileErrorCode.class);
    private static final Map<DistributorFileErrorCode, List<String>> PARAM_KEYS = new EnumMap<>(DistributorFileErrorCode.class);
    private static final Map<DistributorFileErrorCode, String> MESSAGE_KEYS = new EnumMap<>(DistributorFileErrorCode.class);

    static {
        register(DistributorFileErrorCode.FILENAME_SHAPE_INVALID,
                RestErrorCode.DISTRIBUTOR_FILE_FILENAME_SHAPE_INVALID,
                "error.distributor.file.filename.shape.invalid", "filename");
        register(DistributorFileErrorCode.PLANT_REGULATORY_CODE_MISSING,
                RestErrorCode.DISTRIBUTOR_FILE_PLANT_REGULATORY_CODE_MISSING,
                "error.distributor.file.plant.regulatory.code.missing");
        register(DistributorFileErrorCode.FILENAME_REGULATORY_CODE_MISMATCH,
                RestErrorCode.DISTRIBUTOR_FILE_FILENAME_REGULATORY_CODE_MISMATCH,
                "error.distributor.file.filename.regulatory.code.mismatch", "expected", "actual");
        register(DistributorFileErrorCode.VALUE_DECIMAL_SEPARATOR_INVALID,
                RestErrorCode.DISTRIBUTOR_FILE_VALUE_DECIMAL_SEPARATOR_INVALID,
                "error.distributor.file.value.decimal.separator.invalid", "line", "value");
        register(DistributorFileErrorCode.VALUE_SCALE_INVALID,
                RestErrorCode.DISTRIBUTOR_FILE_VALUE_SCALE_INVALID,
                "error.distributor.file.value.scale.invalid", "line", "cups", "value");
        register(DistributorFileErrorCode.CUPS_LENGTH_INVALID,
                RestErrorCode.DISTRIBUTOR_FILE_CUPS_LENGTH_INVALID,
                "error.distributor.file.cups.length.invalid", "line", "cups");
        register(DistributorFileErrorCode.CUPS_DUPLICATE,
                RestErrorCode.DISTRIBUTOR_FILE_CUPS_DUPLICATE,
                "error.distributor.file.cups.duplicate", "cups", "lines");
        register(DistributorFileErrorCode.COEFFICIENT_SUM_INVALID,
                RestErrorCode.DISTRIBUTOR_FILE_COEFFICIENT_SUM_INVALID,
                "error.distributor.file.coefficient.sum.invalid", "actualSum");
        register(DistributorFileErrorCode.CUPS_UNKNOWN,
                RestErrorCode.DISTRIBUTOR_FILE_CUPS_UNKNOWN,
                "error.distributor.file.cups.unknown", "line", "cups");
        register(DistributorFileErrorCode.LINE_MALFORMED,
                RestErrorCode.DISTRIBUTOR_FILE_LINE_MALFORMED,
                "error.distributor.file.line.malformed", "line", "rawLine");
    }

    private static void register(DistributorFileErrorCode code, RestErrorCode restErrorCode, String messageKey,
                                  String... paramKeys) {
        CODES.put(code, restErrorCode);
        MESSAGE_KEYS.put(code, messageKey);
        PARAM_KEYS.put(code, List.of(paramKeys));
    }

    private final MessageSource messageSource;

    public DistributorFileErrorMapper(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public RestErrorDetail toRestErrorDetail(DistributorFileError error, Locale locale) {
        DistributorFileErrorCode code = error.getCode();
        List<String> paramKeys = PARAM_KEYS.get(code);
        Object[] args = paramKeys.stream().map(key -> error.getParams().get(key)).toArray();

        String message = messageSource.getMessage(MESSAGE_KEYS.get(code), args, locale);

        return new RestErrorDetail(message, CODES.get(code), error.getParams());
    }
}
