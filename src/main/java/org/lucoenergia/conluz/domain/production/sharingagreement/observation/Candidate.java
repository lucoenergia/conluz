package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import java.util.UUID;

/**
 * A value a supply's observed β may match: one coefficient row of an agreement, or the implicit 0 of an
 * agreement the supply has no row in.
 *
 * @param row   the coefficient row, or null for the implicit 0
 * @param rank  the agreement's position in registered order
 */
record Candidate(UUID sharingAgreementId, ObservedCoefficient row, double value, int rank) {

    static final double TOLERANCE = 0.001;

    // Absorbs the binary representation error of β and of the coefficient, so a β exactly 0.1 pp away matches.
    private static final double EPSILON = 1e-9;

    static Candidate of(ObservedCoefficient row, int rank) {
        return new Candidate(row.sharingAgreementId(), row, row.coefficient().doubleValue(), rank);
    }

    static Candidate implicitZero(UUID sharingAgreementId, int rank) {
        return new Candidate(sharingAgreementId, null, 0d, rank);
    }

    boolean isImplicitZero() {
        return row == null;
    }

    boolean matches(double observed) {
        return sameValue(value, observed);
    }

    /**
     * Whether two values are the same within the ±0.1 pp tolerance.
     */
    static boolean sameValue(double a, double b) {
        return Math.abs(a - b) <= TOLERANCE + EPSILON;
    }
}
