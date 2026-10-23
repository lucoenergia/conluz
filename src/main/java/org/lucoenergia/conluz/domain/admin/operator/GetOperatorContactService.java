package org.lucoenergia.conluz.domain.admin.operator;

import java.util.Optional;

public interface GetOperatorContactService {

    /**
     * @return the operator contact, or empty when this instance configures none (CTC-004)
     */
    Optional<OperatorContact> getOperatorContact();
}
