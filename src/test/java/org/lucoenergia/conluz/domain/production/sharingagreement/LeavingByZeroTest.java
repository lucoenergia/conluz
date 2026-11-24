package org.lucoenergia.conluz.domain.production.sharingagreement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests of SHA-020, written from the acceptance criteria of #621: A, B and C are the plant's agreements in
 * registered order.
 */
class LeavingByZeroTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();
    private static final List<UUID> ORDER = List.of(A, B, C);

    private static BigDecimal value(String coefficient) {
        return new BigDecimal(coefficient);
    }

    @Test
    @DisplayName("SHA-020 a supply at 4 % in A and 0 % in B, right after A, leaves the distribution in B")
    void dropToZeroLeaves() {
        assertThat(LeavingByZero.leaves(B, value("0.000000"), ORDER, Map.of(A, value("0.04"), B, value("0.000000"))))
                .isTrue();
    }

    @Test
    @DisplayName("SHA-020 a supply whose first agreement gives it 0 % does not leave")
    void zeroInTheFirstAgreementDoesNotLeave() {
        assertThat(LeavingByZero.leaves(A, value("0"), ORDER, Map.of(A, value("0"), B, value("0.03")))).isFalse();
    }

    @Test
    @DisplayName("SHA-020 a supply at 0 % in A and 0 % in B does not leave in B")
    void zeroAfterZeroDoesNotLeave() {
        assertThat(LeavingByZero.leaves(B, value("0"), ORDER, Map.of(A, value("0.000000"), B, value("0"))))
                .isFalse();
    }

    @Test
    @DisplayName("SHA-020 a supply at 4 % in A, absent from B and at 0 % in C does not leave in C")
    void zeroAfterAnAbsenceDoesNotLeave() {
        assertThat(LeavingByZero.leaves(C, value("0"), ORDER, Map.of(A, value("0.04"), C, value("0")))).isFalse();
    }

    @Test
    @DisplayName("SHA-020 a supply at 4 % in A and 3 % in B does not leave")
    void nonZeroValueDoesNotLeave() {
        assertThat(LeavingByZero.leaves(B, value("0.03"), ORDER, Map.of(A, value("0.04"), B, value("0.03"))))
                .isFalse();
    }

    @Test
    @DisplayName("SHA-020 a DRAFT between A and B is ignored when finding the previous agreement")
    void draftIsNotThePreviousAgreement() {
        UUID draft = UUID.randomUUID();

        // The draft is not in the registered order of the agreements in scope, whatever value it gives the supply.
        assertThat(LeavingByZero.leaves(B, value("0"), List.of(A, B),
                Map.of(A, value("0.04"), draft, value("0"), B, value("0")))).isTrue();
        assertThat(LeavingByZero.leaves(B, value("0"), List.of(A, B),
                Map.of(A, value("0"), draft, value("0.04"), B, value("0")))).isFalse();
    }
}
