package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

/**
 * How/why a coefficient's coverage ends.
 *
 * <p>"Later" never means created later: since an agreement created later may hold an earlier period, an applied
 * coefficient is compared by validity (SHA-016) and a pending one by the registered order of its agreement
 * (SHA-017). DRAFT agreements other than the one read never count.
 */
public enum CoefficientEndState {

    /**
     * No {@code validTo}, and nothing later ends or succeeds it: the coefficient is still in force, or (when
     * pending) its agreement is the last one registered.
     */
    OPEN,

    /**
     * No {@code validTo}, but the supply appears to have left without an authored close -- the end must be
     * authored by hand. For an applied coefficient: another agreement has a coefficient starting after it and none
     * for this supply. For a pending one: the next agreement in registered order has no coefficient for this supply.
     */
    OPEN_ORPHAN,

    /**
     * No {@code validTo}; the same supply has a coefficient that has not been applied yet ({@code validFrom} still
     * null) in an agreement after this one in registered order -- any such agreement for an applied coefficient,
     * the next one for a pending coefficient.
     */
    PENDING_SUCCESSION,

    /**
     * The end coincides with an already-applied successor coefficient for the same supply -- either
     * because no {@code validTo} is stored and the next coefficient is already activated, or because
     * the stored {@code validTo} was written automatically by the activation cascade rather than
     * authored explicitly.
     */
    DERIVED,

    /**
     * {@code validTo} is stored and was authored explicitly (a close/baja), not written by the
     * activation cascade. Reopenable via the reopen endpoint.
     */
    CLOSED
}
