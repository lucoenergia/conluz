package org.lucoenergia.conluz.domain.production.sharingagreement;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Whether a coefficient row leaves the distribution by dropping to 0 (SHA-020): its value is 0 and, in the agreement
 * immediately before its own in registered order, the supply holds a row greater than 0.
 *
 * <p>A supply absent from that previous agreement already left the distribution there, by absence, so its 0 here is
 * not a second leaving. The same rule serves the date effects and observed sharing, so both report the same rows.</p>
 */
public final class LeavingByZero {

    private LeavingByZero() {
    }

    /**
     * @param sharingAgreementId the row's agreement
     * @param coefficient        the row's value
     * @param registeredOrder    the ids of the plant's PUBLISHED and SUPERSEDED agreements in registered order, the
     *                           row's own included; an agreement left out of it, such as a DRAFT, is never the
     *                           previous one
     * @param supplyCoefficients the supply's value in each agreement it holds a row in, by agreement id
     */
    public static boolean leaves(UUID sharingAgreementId, BigDecimal coefficient, List<UUID> registeredOrder,
                                 Map<UUID, BigDecimal> supplyCoefficients) {
        if (coefficient.signum() != 0) {
            return false;
        }
        int position = registeredOrder.indexOf(sharingAgreementId);
        if (position < 1) {
            return false;
        }
        BigDecimal previous = supplyCoefficients.get(registeredOrder.get(position - 1));
        return previous != null && previous.signum() > 0;
    }
}
