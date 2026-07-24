package org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.activation;

import org.lucoenergia.conluz.domain.production.plant.sharingagreement.activation.CoefficientActivationError;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.activation.CoefficientActivationErrorCode;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorCode;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorDetail;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps a domain {@link CoefficientActivationError} (no web-layer type) to the typed
 * {@link RestErrorDetail} contract. An explicit {@link EnumMap} pairing, rather than a naming
 * convention derived at runtime, so a drift between {@link CoefficientActivationErrorCode} and
 * {@link RestErrorCode} fails to compile instead of failing at runtime. Mirrors
 * {@code DistributorFileErrorMapper}.
 */
@Component
public class CoefficientActivationErrorMapper {

    private static final Map<CoefficientActivationErrorCode, RestErrorCode> CODES = new EnumMap<>(CoefficientActivationErrorCode.class);
    private static final Map<CoefficientActivationErrorCode, List<String>> PARAM_KEYS = new EnumMap<>(CoefficientActivationErrorCode.class);
    private static final Map<CoefficientActivationErrorCode, String> MESSAGE_KEYS = new EnumMap<>(CoefficientActivationErrorCode.class);

    static {
        register(CoefficientActivationErrorCode.COEFFICIENT_NOT_IN_AGREEMENT,
                RestErrorCode.SHARING_AGREEMENT_COEFFICIENT_NOT_IN_AGREEMENT,
                "error.sharing.agreement.coefficient.not.in.agreement", "coefficientId");
        register(CoefficientActivationErrorCode.DATE_IN_FUTURE,
                RestErrorCode.SHARING_AGREEMENT_DATE_IN_FUTURE,
                "error.sharing.agreement.date.in.future", "coefficientId", "cups");
        register(CoefficientActivationErrorCode.ACTIVATION_DATE_NOT_AFTER_PREDECESSOR,
                RestErrorCode.SHARING_AGREEMENT_ACTIVATION_DATE_NOT_AFTER_PREDECESSOR,
                "error.sharing.agreement.activation.date.not.after.predecessor", "coefficientId", "cups");
        register(CoefficientActivationErrorCode.ACTIVATION_DATE_NOT_BEFORE_SUCCESSOR,
                RestErrorCode.SHARING_AGREEMENT_ACTIVATION_DATE_NOT_BEFORE_SUCCESSOR,
                "error.sharing.agreement.activation.date.not.before.successor", "coefficientId", "cups");
        register(CoefficientActivationErrorCode.COEFFICIENT_HAS_SUCCESSOR,
                RestErrorCode.SHARING_AGREEMENT_COEFFICIENT_HAS_SUCCESSOR,
                "error.sharing.agreement.coefficient.has.successor", "coefficientId", "cups");
        register(CoefficientActivationErrorCode.COEFFICIENT_NOT_ACTIVE,
                RestErrorCode.SHARING_AGREEMENT_COEFFICIENT_NOT_ACTIVE,
                "error.sharing.agreement.coefficient.not.active", "coefficientId", "cups");
        register(CoefficientActivationErrorCode.CLOSURE_DATE_NOT_AFTER_ACTIVATION,
                RestErrorCode.SHARING_AGREEMENT_CLOSURE_DATE_NOT_AFTER_ACTIVATION,
                "error.sharing.agreement.closure.date.not.after.activation", "coefficientId", "cups");
    }

    private static void register(CoefficientActivationErrorCode code, RestErrorCode restErrorCode, String messageKey,
                                  String... paramKeys) {
        CODES.put(code, restErrorCode);
        MESSAGE_KEYS.put(code, messageKey);
        PARAM_KEYS.put(code, List.of(paramKeys));
    }

    private final MessageSource messageSource;

    public CoefficientActivationErrorMapper(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public RestErrorDetail toRestErrorDetail(CoefficientActivationError error, Locale locale) {
        CoefficientActivationErrorCode code = error.getCode();
        List<String> paramKeys = PARAM_KEYS.get(code);
        Object[] args = paramKeys.stream().map(key -> error.getParams().get(key)).toArray();

        String message = messageSource.getMessage(MESSAGE_KEYS.get(code), args, locale);

        return new RestErrorDetail(message, CODES.get(code), error.getParams());
    }
}
