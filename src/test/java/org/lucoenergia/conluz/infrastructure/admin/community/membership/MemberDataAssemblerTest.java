package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembershipMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.MemberDataView;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.admin.community.access.AccessPolicies;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberDataAssemblerTest {

    @Mock
    private GetMembershipsRepository getMembershipsRepository;

    private MemberDataAssembler assembler() {
        return new MemberDataAssembler(new AccessPolicies(), getMembershipsRepository);
    }

    // --- supply owners ---

    @Test
    @DisplayName("MEM-026 Each supply on a page shows its owner's record in that supply's community, from one query")
    void recordsOfSupplyOwners_readsEachOwnersRecordInTheSupplysCommunity_inOneQuery() {
        Community a = community();
        Community b = community();
        User p = UserMother.randomUser();
        User q = UserMother.randomUser();
        CommunityMembership pInA = record(p, a, "P in A");
        CommunityMembership pInB = record(p, b, "P in B");
        // Q's record in B is in the query's result but belongs to no supply's pair: Q's supply is in A
        CommunityMembership qInB = record(q, b, "Q in B");
        Supply pSupplyInA = supplyIn(a, p);
        Supply pSupplyInB = supplyIn(b, p);
        Supply qSupplyInA = supplyIn(a, q);
        when(getMembershipsRepository.findByUserIdsAndCommunityIds(Set.of(p.getId(), q.getId()),
                Set.of(a.getId(), b.getId())))
                .thenReturn(List.of(pInA, pInB, qInB));

        Function<Supply, CommunityMembership> ownerRecords =
                assembler().recordsOfSupplyOwners(List.of(pSupplyInA, pSupplyInB, qSupplyInA));

        assertSame(pInA, ownerRecords.apply(pSupplyInA));
        assertSame(pInB, ownerRecords.apply(pSupplyInB));
        assertNull(ownerRecords.apply(qSupplyInA));
        verify(getMembershipsRepository, times(1)).findByUserIdsAndCommunityIds(
                Set.of(p.getId(), q.getId()), Set.of(a.getId(), b.getId()));
    }

    @Test
    @DisplayName("MEM-026 A supply without an owner has no owner record")
    void recordsOfSupplyOwners_isNull_forASupplyWithoutAnOwner() {
        Supply ownerless = supplyIn(community(), null);

        assertNull(assembler().recordsOfSupplyOwners(List.of(ownerless)).apply(ownerless));
    }

    @Test
    @DisplayName("MEM-026 One supply shows its owner's record in its community")
    void recordOfSupplyOwner_readsTheOwnersRecordInTheSupplysCommunity() {
        Community a = community();
        User p = UserMother.randomUser();
        CommunityMembership pInA = record(p, a, "P in A");
        Supply supply = supplyIn(a, p);
        when(getMembershipsRepository.findByUserIdsAndCommunityIds(Set.of(p.getId()), Set.of(a.getId())))
                .thenReturn(List.of(pInA));

        assertSame(pInA, assembler().recordOfSupplyOwner(supply));
    }

    @Test
    @DisplayName("MEM-026 A supply whose owner has no record in its community has no owner record, not the account")
    void recordOfSupplyOwner_isNull_withoutARecordInTheSupplysCommunity() {
        Supply supply = supplyIn(community(), UserMother.randomUser());

        assertNull(assembler().recordOfSupplyOwner(supply));
    }

    // --- users ---

    @Test
    @DisplayName("MEM-026 A community admin sees the record from the memberships the user carries, with no query")
    void forUserWithLoadedMemberships_readsTheCarriedRecord_withoutAQuery() {
        Community a = community();
        Community b = community();
        User target = UserMother.randomUser();
        CommunityMembership targetInA = record(target, a, "Target in A");
        target.setMemberships(List.of(record(target, b, "Target in B"), targetInA));

        assertEquals(MemberDataView.ofRecord(targetInA),
                assembler().forUserWithLoadedMemberships(adminOf(a), target));
        verifyNoInteractions(getMembershipsRepository);
    }

    // --- Fixtures ---

    private static Community community() {
        return CommunityMother.random().build();
    }

    private static User adminOf(Community community) {
        User admin = UserMother.randomUser();
        admin.setMemberships(List.of(CommunityMembershipMother.active()
                .withId(UUID.randomUUID())
                .withUser(admin)
                .withCommunity(community)
                .withRole(CommunityRole.COMMUNITY_ADMIN)
                .withEnabled(true)
                .build()));
        return admin;
    }

    private static CommunityMembership record(User user, Community community, String fullName) {
        return CommunityMembershipMother.active()
                .withId(UUID.randomUUID())
                .withUser(user)
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

    private static Supply supplyIn(Community community, User owner) {
        return new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode("ES001")
                .withUser(owner)
                .withCommunity(community)
                .withName("Supply")
                .withAddress("Address")
                .withEnabled(true)
                .build();
    }
}
