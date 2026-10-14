package org.lucoenergia.conluz.infrastructure.admin.community;

/**
 * The JPQL spellings of the membership states that queries filter on in the database, each the same condition as a
 * method of {@link org.lucoenergia.conluz.domain.admin.community.CommunityMembership}.
 *
 * <p>Compile-time constants, so a {@code @Query} can concatenate them. They expect the membership to be aliased
 * {@code m}. Every query that filters memberships on their state uses one of them, which
 * {@code MembershipStateArchTest} checks.</p>
 */
public final class ActiveMembershipJpql {

    /**
     * An active membership: accepted and enabled (ADR-0003 §2), as
     * {@link org.lucoenergia.conluz.domain.admin.community.CommunityMembership#isActive()}.
     */
    public static final String ACTIVE_MEMBERSHIP = "m.acceptedAt IS NOT NULL AND m.enabled = true";

    /**
     * A membership awaiting acceptance: pending and enabled, as
     * {@link org.lucoenergia.conluz.domain.admin.community.CommunityMembership#isAwaitingAcceptance()}.
     */
    public static final String AWAITING_ACCEPTANCE = "m.acceptedAt IS NULL AND m.enabled = true";

    private ActiveMembershipJpql() {
    }
}
