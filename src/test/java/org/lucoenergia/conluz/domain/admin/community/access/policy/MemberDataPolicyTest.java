package org.lucoenergia.conluz.domain.admin.community.access.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembershipMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.MemberData;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemberDataPolicyTest {

    private final MemberDataPolicy policy = new MemberDataPolicy();

    // --- inCommunity ---

    @Test
    @DisplayName("MEM-026 Inside a community, the member data is the record")
    void inCommunity_isTheRecord() {
        CommunityMembership record = record(PolicyFixtures.community(), "Name in A");

        assertEquals(MemberData.ofRecord(record), policy.inCommunity(record));
    }

    @Test
    @DisplayName("MEM-026 Inside a community, a user without a record has no member data")
    void inCommunity_isAbsent_withoutARecord() {
        assertEquals(new MemberData("", null, null, null, null), policy.inCommunity(null));
    }

    // --- forUser ---

    @Test
    @DisplayName("MEM-026 A user reading themselves sees their account, without loading memberships")
    void forUser_isTheAccount_forTheUserThemselves() {
        User user = UserMother.randomUser();
        AtomicInteger loads = new AtomicInteger();

        assertEquals(MemberData.ofAccount(user), policy.forUser(user, user, counting(loads, List.of())));
        assertEquals(0, loads.get());
    }

    @Test
    @DisplayName("MEM-026 The platform admin sees the account, without loading memberships")
    void forUser_isTheAccount_forThePlatformAdmin() {
        Community community = PolicyFixtures.community();
        User target = UserMother.randomUser();
        AtomicInteger loads = new AtomicInteger();

        assertEquals(MemberData.ofAccount(target), policy.forUser(PolicyFixtures.platformAdmin(), target,
                counting(loads, List.of(record(community, "Name in A")))));
        assertEquals(0, loads.get());
    }

    @Test
    @DisplayName("MEM-026 A community admin sees the record of the community they administer")
    void forUser_isTheRecordOfTheAdministeredCommunity() {
        Community administered = PolicyFixtures.community();
        Community other = PolicyFixtures.community();
        CommunityMembership recordInAdministered = record(administered, "Name in A");

        assertEquals(MemberData.ofRecord(recordInAdministered),
                policy.forUser(PolicyFixtures.adminOf(administered), UserMother.randomUser(),
                        () -> List.of(record(other, "Name in B"), recordInAdministered)));
    }

    @Test
    @DisplayName("MEM-026 An admin of several shared communities sees the record of the lowest id in text order")
    void forUser_isTheRecordOfTheLowestCommunityIdInTextOrder() {
        // UUID.compareTo compares signed longs, so it puts 8000… before 7fff…; PostgreSQL and the text form do not
        Community low = communityWithId("7fffffff-ffff-ffff-ffff-ffffffffffff");
        Community high = communityWithId("80000000-0000-0000-0000-000000000000");
        CommunityMembership recordInLow = record(low, "Name in low");
        CommunityMembership recordInHigh = record(high, "Name in high");

        MemberData seen = policy.forUser(adminOf(low, high), UserMother.randomUser(),
                () -> List.of(recordInHigh, recordInLow));

        assertEquals(MemberData.ofRecord(recordInLow), seen);
    }

    @Test
    @DisplayName("MEM-026 A caller sharing no administered community with the user sees no member data")
    void forUser_isAbsent_withoutASharedAdministeredCommunity() {
        Community community = PolicyFixtures.community();

        assertEquals(MemberData.absent(), policy.forUser(PolicyFixtures.adminOf(PolicyFixtures.community()),
                UserMother.randomUser(), () -> List.of(record(community, "Name in A"))));
        assertEquals(MemberData.absent(), policy.forUser(PolicyFixtures.memberOf(community),
                UserMother.randomUser(), () -> List.of(record(community, "Name in A"))));
    }

    @Test
    @DisplayName("MEM-026 A pending admin's community does not count as administered")
    void forUser_ignoresAPendingAdminMembership() {
        Community community = PolicyFixtures.community();

        assertEquals(MemberData.absent(), policy.forUser(PolicyFixtures.pendingAdminOf(community),
                UserMother.randomUser(), () -> List.of(record(community, "Name in A"))));
    }

    // --- Fixtures ---

    private static CommunityMembership record(Community community, String fullName) {
        return CommunityMembershipMother.active()
                .withId(UUID.randomUUID())
                .withCommunity(community)
                .withRole(CommunityRole.COMMUNITY_MEMBER)
                .withEnabled(true)
                .withFullName(fullName)
                .withMemberNumber(7)
                .withContactEmail(fullName.replace(' ', '.') + "@example.org")
                .withAddress("Calle " + fullName)
                .withPhoneNumber("600000000")
                .build();
    }

    private static Community communityWithId(String id) {
        return CommunityMother.random().withId(UUID.fromString(id)).build();
    }

    private static User adminOf(Community... communities) {
        User admin = UserMother.randomUser();
        List<CommunityMembership> memberships = new ArrayList<>();
        for (Community community : communities) {
            memberships.add(PolicyFixtures.membershipIn(community, CommunityRole.COMMUNITY_ADMIN, true));
        }
        admin.setMemberships(memberships);
        return admin;
    }

    private static Supplier<List<CommunityMembership>> counting(AtomicInteger loads,
                                                                List<CommunityMembership> memberships) {
        return () -> {
            loads.incrementAndGet();
            return memberships;
        };
    }
}
