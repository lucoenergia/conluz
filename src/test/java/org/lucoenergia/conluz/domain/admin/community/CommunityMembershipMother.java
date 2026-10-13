package org.lucoenergia.conluz.domain.admin.community;

import java.time.Instant;

public class CommunityMembershipMother {

    /**
     * The instant every {@link #active()} membership was accepted at. Fixed, so that two fixtures built
     * the same way are equal field by field.
     */
    public static final Instant ACCEPTED_AT = Instant.parse("2026-01-01T00:00:00Z");

    /**
     * A builder for an active membership: accepted, and enabled unless the caller sets otherwise. A
     * bare {@link CommunityMembership.Builder} leaves the acceptance unset, which makes the membership
     * pending.
     */
    public static CommunityMembership.Builder active() {
        return new CommunityMembership.Builder().withAcceptedAt(ACCEPTED_AT);
    }
}
