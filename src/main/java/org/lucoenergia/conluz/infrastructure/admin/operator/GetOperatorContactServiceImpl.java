package org.lucoenergia.conluz.infrastructure.admin.operator;

import org.lucoenergia.conluz.domain.admin.operator.GetOperatorContactService;
import org.lucoenergia.conluz.domain.admin.operator.OperatorContact;
import org.lucoenergia.conluz.domain.admin.operator.OperatorContactDraft;
import org.lucoenergia.conluz.domain.admin.operator.OperatorContactInvalidException;
import org.lucoenergia.conluz.domain.admin.operator.OperatorContactValidator;
import org.lucoenergia.conluz.domain.admin.operator.OperatorContactViolationCode;
import org.lucoenergia.conluz.domain.shared.contact.PhoneNumberFormatChecker;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.stream.Collectors;

import static org.lucoenergia.conluz.infrastructure.admin.operator.OperatorContactProperties.EMAIL_VARIABLE;
import static org.lucoenergia.conluz.infrastructure.admin.operator.OperatorContactProperties.NAME_VARIABLE;
import static org.lucoenergia.conluz.infrastructure.admin.operator.OperatorContactProperties.PHONE_VARIABLE;

/**
 * Validates the configured operator contact once, when the application starts, so a misconfiguration stops it from
 * starting (CTC-005), and then only returns the result. A refusal is reported by the environment variables an
 * operator sets, since those, not the domain's violation codes, are what the operator has to fix.
 *
 * <p>Not {@code @Transactional}: it never touches the database, and it answers an unauthenticated endpoint, where a
 * transaction would hold a pool connection for every anonymous call.</p>
 */
@Service
@EnableConfigurationProperties(OperatorContactProperties.class)
public class GetOperatorContactServiceImpl implements GetOperatorContactService {

    private final Optional<OperatorContact> operatorContact;

    public GetOperatorContactServiceImpl(OperatorContactProperties properties, OperatorContactValidator validator) {
        try {
            this.operatorContact = validator.validate(new OperatorContactDraft(properties.name(), properties.email(),
                    properties.phone(), properties.schedule(), properties.notes()));
        } catch (OperatorContactInvalidException e) {
            throw new IllegalStateException("The operator contact is misconfigured: "
                    + e.getViolations().stream().map(GetOperatorContactServiceImpl::describe)
                    .collect(Collectors.joining("; "))
                    + ". Set at least " + NAME_VARIABLE + " and " + EMAIL_VARIABLE
                    + ", or none of the CONLUZ_OPERATOR_CONTACT_* variables.", e);
        }
    }

    @Override
    public Optional<OperatorContact> getOperatorContact() {
        return operatorContact;
    }

    private static String describe(OperatorContactViolationCode violation) {
        return switch (violation) {
            case NAME_MISSING -> NAME_VARIABLE + " is not set";
            case EMAIL_MISSING -> EMAIL_VARIABLE + " is not set";
            case EMAIL_INVALID -> EMAIL_VARIABLE + " is not an email address";
            case PHONE_CHARACTERS_INVALID -> PHONE_VARIABLE + " may only have digits, spaces and a leading +";
            case PHONE_TOO_FEW_DIGITS -> PHONE_VARIABLE + " must have at least " + PhoneNumberFormatChecker.MIN_DIGITS
                    + " digits";
        };
    }
}
