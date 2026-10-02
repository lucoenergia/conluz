package org.lucoenergia.conluz.infrastructure.admin.user.auth;

import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The security principal is loaded on every authenticated request, and every access rule then reads
 * the community id of each of its memberships through {@code CallerMemberships}. If those
 * communities arrive as proxies, each rule evaluation pays a select per membership — on every
 * request of every session, not merely on a listing.
 *
 * <p>So this asserts the shape of the load rather than any access outcome: the memberships come
 * back carrying a real community and a real user, and adding memberships does not add queries.</p>
 */
@Transactional
class PrincipalMembershipLoadingTest extends BaseIntegrationTest {

    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipService createMembershipService;

    @Test
    void thePrincipalCarriesItsMembershipsWithTheirCommunityAndUserResolved() {
        User user = persistUserIn(3);

        User principal = (User) userDetailsService.loadUserByUsername(user.getPersonalId());

        List<CommunityMembership> memberships = principal.getMemberships();
        assertEquals(3, memberships.size());
        for (CommunityMembership membership : memberships) {
            assertNotNull(membership.getCommunity(), "the community must be resolved, not a proxy");
            assertNotNull(membership.getCommunity().getId(),
                    "CallerMemberships reads this id for every access rule");
            assertNotNull(membership.getUser(), "the membership's user must be resolved too");
        }
    }

    /**
     * The regression this commit exists to prevent. The count itself is not asserted -- only that it
     * does not grow with the number of communities the caller belongs to.
     */
    @Test
    void loadingThePrincipalCostsTheSameForOneCommunityAndForFive() {
        User oneCommunity = persistUserIn(1);
        User fiveCommunities = persistUserIn(5);

        long forOne = statementsForLoading(oneCommunity);
        long forFive = statementsForLoading(fiveCommunities);

        assertEquals(forOne, forFive,
                "loading the principal must not issue a query per membership: it happens on every request");
    }

    private long statementsForLoading(User user) {
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        userDetailsService.loadUserByUsername(user.getPersonalId());

        return statistics.getPrepareStatementCount();
    }

    private User persistUserIn(int communities) {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        for (int i = 0; i < communities; i++) {
            Community community = createCommunityRepository.create(CommunityMother.random().build());
            createMembershipService.create(community.getId(), user.getId(), CommunityRole.COMMUNITY_MEMBER);
        }
        return user;
    }
}
