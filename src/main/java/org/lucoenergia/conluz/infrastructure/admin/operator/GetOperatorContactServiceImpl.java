package org.lucoenergia.conluz.infrastructure.admin.operator;

import org.lucoenergia.conluz.domain.admin.operator.GetOperatorContactService;
import org.lucoenergia.conluz.domain.admin.operator.OperatorContact;
import org.lucoenergia.conluz.domain.shared.contact.ContactEmailValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Validates the operator contact settings once, when the application starts, so a misconfiguration stops it from
 * starting (CTC-005), and then only returns the result.
 *
 * <p>Not {@code @Transactional}: it never touches the database, and it answers an unauthenticated endpoint, where a
 * transaction would hold a pool connection for every anonymous call.</p>
 */
@Service
@EnableConfigurationProperties(OperatorContactProperties.class)
public class GetOperatorContactServiceImpl implements GetOperatorContactService {

    private final Optional<OperatorContact> operatorContact;

    public GetOperatorContactServiceImpl(OperatorContactProperties properties, ContactEmailValidator emailValidity) {
        this.operatorContact = new OperatorContactPropertiesValidator(emailValidity).validate(properties);
    }

    @Override
    public Optional<OperatorContact> getOperatorContact() {
        return operatorContact;
    }
}
