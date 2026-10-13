package org.lucoenergia.conluz.infrastructure.admin.community;

import java.util.UUID;

/**
 * Turns a membership created through the membership repository or service into a pending or a
 * disabled one. No repository does either yet (#392 creates pending memberships, #395 disables them), so
 * the state is set on the loaded entity, and every other column keeps the value production gave it.
 */
public final class MembershipStateTestSupport {

    private MembershipStateTestSupport() {
    }

    /**
     * Marks the membership pending: not accepted. It stays enabled.
     */
    public static void markPending(CommunityMembershipJpaRepository repository, UUID userId, UUID communityId) {
        CommunityMembershipEntity membership = load(repository, userId, communityId);
        membership.setAcceptedAt(null);
        repository.saveAndFlush(membership);
    }

    /**
     * Marks the membership disabled. It stays accepted.
     */
    public static void markDisabled(CommunityMembershipJpaRepository repository, UUID userId, UUID communityId) {
        CommunityMembershipEntity membership = load(repository, userId, communityId);
        membership.setEnabled(false);
        repository.saveAndFlush(membership);
    }

    private static CommunityMembershipEntity load(CommunityMembershipJpaRepository repository, UUID userId,
                                                  UUID communityId) {
        return repository.findByUserIdAndCommunityId(userId, communityId).orElseThrow();
    }
}
