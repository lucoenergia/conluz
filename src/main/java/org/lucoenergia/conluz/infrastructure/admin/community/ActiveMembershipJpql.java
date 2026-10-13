package org.lucoenergia.conluz.infrastructure.admin.community;

/**
 * The JPQL spelling of an active membership, for queries that have to filter on it in the database.
 * It is the same condition as {@link org.lucoenergia.conluz.domain.admin.community.CommunityMembership#isActive()}:
 * accepted and enabled (ADR-0003 §2).
 *
 * <p>A compile-time constant, so a {@code @Query} can concatenate it. It expects the membership to
 * be aliased {@code m}. Every query that filters memberships on their state uses it, which
 * {@code MembershipStateArchTest} checks.</p>
 */
public final class ActiveMembershipJpql {

    public static final String ACTIVE_MEMBERSHIP = "m.acceptedAt IS NOT NULL AND m.enabled = true";

    private ActiveMembershipJpql() {
    }
}
