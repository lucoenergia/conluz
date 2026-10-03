package org.lucoenergia.conluz.domain.admin.supply;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyServiceImpl;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CreateSupplyServiceTest {

    private final GetUserRepository userRepository = mock(GetUserRepository.class);
    private final CreateSupplyRepository supplyRepository = mock(CreateSupplyRepository.class);
    private final GetMembershipsRepository getMembershipsRepository = mock(GetMembershipsRepository.class);
    private final CreateSupplyService createSupplyService =
            new CreateSupplyServiceImpl(supplyRepository, userRepository, getMembershipsRepository);

    @Test
    void testCreateSupplyWithUserPersonalIdWhenUserExist_success() {
        // arrange
        Supply expectedSupply = new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode("code")
                .withAddress("address")
                .withEnabled(Boolean.TRUE)
                .build();

        User user = UserMother.randomUser();
        UUID communityId = UUID.randomUUID();
        when(userRepository.findByPersonalId(any(UserPersonalId.class))).thenReturn(Optional.of(user));
        when(getMembershipsRepository.findByUserIdAndCommunityId(user.getId(), communityId))
                .thenReturn(Optional.of(membership(user, true)));
        when(supplyRepository.create(any(Supply.class), any(UserId.class), any())).thenReturn(expectedSupply);

        // act
        Supply actualSupply = createSupplyService.create(expectedSupply, UserPersonalId.of(user.getPersonalId()),
                communityId);

        // assert
        assertNotNull(actualSupply);
        assertEquals(expectedSupply, actualSupply);
    }

    @Test
    void testCreateSupplyWithUserPersonalIdWhenUserNotExistThrowUserNotFoundException() {
        // arrange
        Supply supply = new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode("code")
                .withAddress("address")
                .withEnabled(Boolean.TRUE)
                .build();

        UUID communityId = UUID.randomUUID();

        when(userRepository.findByPersonalId(any(UserPersonalId.class))).thenReturn(Optional.empty());

        // act & assert
        assertThrows(UserNotFoundException.class, () -> createSupplyService.create(supply, UserPersonalId.of("123"),
                communityId));
        verifyNoInteractions(getMembershipsRepository);
        verifyNoInteractions(supplyRepository);
    }

    @Test
    void createRejectsAnOwnerWithoutMembershipInTheCommunityExactlyLikeAnUnknownOne() {
        Supply supply = SupplyMother.random().build();
        User user = UserMother.randomUser();
        UUID communityId = UUID.randomUUID();
        UserPersonalId personalId = UserPersonalId.of(user.getPersonalId());
        when(userRepository.findByPersonalId(personalId)).thenReturn(Optional.of(user));
        when(getMembershipsRepository.findByUserIdAndCommunityId(user.getId(), communityId))
                .thenReturn(Optional.empty());

        UserNotFoundException exception = assertThrows(UserNotFoundException.class,
                () -> createSupplyService.create(supply, personalId, communityId));

        // Same constructor and argument as for an unknown personalId, so the handlers render the same answer.
        assertEquals(Optional.of(personalId), exception.getUserPersonalId());
        assertEquals(Optional.empty(), exception.getUserId());
        verify(supplyRepository, never()).create(any(), any());
        verify(supplyRepository, never()).create(any(), any(), any());
    }

    @Test
    void createAcceptsAnOwnerWhoseMembershipInTheCommunityIsDisabled() {
        Supply supply = SupplyMother.random().build();
        User user = UserMother.randomUser();
        UUID communityId = UUID.randomUUID();
        when(userRepository.findByPersonalId(any(UserPersonalId.class))).thenReturn(Optional.of(user));
        when(getMembershipsRepository.findByUserIdAndCommunityId(user.getId(), communityId))
                .thenReturn(Optional.of(membership(user, false)));
        when(supplyRepository.create(any(Supply.class), any(UserId.class), any())).thenReturn(supply);

        Supply created = createSupplyService.create(supply, UserPersonalId.of(user.getPersonalId()), communityId);

        assertEquals(supply, created);
        verify(supplyRepository).create(eq(supply), argThat(id -> user.getId().equals(id.getId())), eq(communityId));
    }

    private static CommunityMembership membership(User user, boolean enabled) {
        return new CommunityMembership.Builder()
                .withId(UUID.randomUUID())
                .withUser(user)
                .withRole(CommunityRole.COMMUNITY_MEMBER)
                .withEnabled(enabled)
                .build();
    }
}
