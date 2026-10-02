package org.lucoenergia.conluz.infrastructure.admin.user.get;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.get.UserScope;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.pagination.Direction;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GetUserServiceImplTest {

    private final GetUserRepository getUserRepository = Mockito.mock(GetUserRepository.class);
    private final GetMembershipsRepository getMembershipsRepository = Mockito.mock(GetMembershipsRepository.class);
    private final GetUserServiceImpl getUserService =
            new GetUserServiceImpl(getUserRepository, getMembershipsRepository);

    @Test
    void findById_shouldReturnUserWhenUserExists() {
        // Given
        UUID userId = UUID.randomUUID();
        User expectedUser = UserMother.randomUserWithId(userId);
        UserId userIdValue = UserId.of(userId);

        when(getUserRepository.findById(userIdValue)).thenReturn(Optional.of(expectedUser));
        when(getMembershipsRepository.findByUserId(userId)).thenReturn(List.of());

        // When
        User result = getUserService.findById(userIdValue);

        // Then
        assertNotNull(result);
        assertEquals(expectedUser.getId(), result.getId());
        assertEquals(expectedUser.getPersonalId(), result.getPersonalId());
        assertEquals(expectedUser.getFullName(), result.getFullName());
        verify(getUserRepository).findById(userIdValue);
    }

    @Test
    void findById_shouldPopulateMemberships() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = UserMother.randomUserWithId(userId);
        UserId userIdValue = UserId.of(userId);
        CommunityMembership membership = randomMembership(user);

        when(getUserRepository.findById(userIdValue)).thenReturn(Optional.of(user));
        when(getMembershipsRepository.findByUserId(userId)).thenReturn(List.of(membership));

        // When
        User result = getUserService.findById(userIdValue);

        // Then
        assertEquals(1, result.getMemberships().size());
        assertEquals(membership, result.getMemberships().get(0));
        verify(getMembershipsRepository).findByUserId(userId);
    }

    @Test
    void findById_shouldThrowUserNotFoundExceptionWhenUserDoesNotExist() {
        // Given
        UUID userId = UUID.randomUUID();
        UserId userIdValue = UserId.of(userId);

        when(getUserRepository.findById(userIdValue)).thenReturn(Optional.empty());

        // When & Then
        UserNotFoundException exception = assertThrows(UserNotFoundException.class, () -> {
            getUserService.findById(userIdValue);
        });

        assertEquals(userId.toString(), exception.getId());
        verify(getUserRepository).findById(userIdValue);
        verify(getMembershipsRepository, never()).findByUserId(any());
    }

    @Test
    void findAll_shouldPopulateMembershipsWithSingleBatchQuery() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = UserMother.randomUserWithId(userId);
        CommunityMembership membership = randomMembership(user);
        PagedResult<User> page = new PagedResult<>(List.of(user), 1, 1, 1, 0);

        when(getUserRepository.findAll(any(PagedRequest.class))).thenReturn(page);
        when(getMembershipsRepository.findByUserIds(List.of(userId)))
                .thenReturn(Map.of(userId, List.of(membership)));

        // When
        PagedResult<User> result = getUserService.findAll(PagedRequest.of(0, 10));

        // Then
        assertEquals(1, result.getItems().get(0).getMemberships().size());
        assertEquals(membership, result.getItems().get(0).getMemberships().get(0));
        // Single batch query, not one per user
        verify(getMembershipsRepository, times(1)).findByUserIds(List.of(userId));
        verify(getMembershipsRepository, never()).findByUserId(any());
    }

    @Test
    void findAll_shouldLeaveMembershipsEmptyWhenUserHasNone() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = UserMother.randomUserWithId(userId);
        PagedResult<User> page = new PagedResult<>(List.of(user), 1, 1, 1, 0);

        when(getUserRepository.findAll(any(PagedRequest.class))).thenReturn(page);
        when(getMembershipsRepository.findByUserIds(List.of(userId))).thenReturn(Map.of());

        // When
        PagedResult<User> result = getUserService.findAll(PagedRequest.of(0, 10));

        // Then
        assertTrue(result.getItems().get(0).getMemberships().isEmpty());
    }

    @Test
    void findAllVisible_queriesEveryUser_whenTheScopeIsUnrestricted() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = UserMother.randomUserWithId(userId);
        PagedResult<User> page = new PagedResult<>(List.of(user), 1, 1, 1, 0);
        when(getUserRepository.findAll(any(PagedRequest.class))).thenReturn(page);
        when(getMembershipsRepository.findByUserIds(List.of(userId))).thenReturn(Map.of());

        // When
        PagedResult<User> result = getUserService.findAllVisible(PagedRequest.of(0, 10), UserScope.all());

        // Then
        assertEquals(List.of(user), result.getItems());
        verify(getUserRepository, never()).findAllVisible(any(), any(), any());
    }

    @Test
    void findAllVisible_queriesTheScope_whenTheScopeIsRestricted() {
        // Given
        UUID selfId = UUID.randomUUID();
        Set<UUID> communityIds = Set.of(UUID.randomUUID(), UUID.randomUUID());
        when(getUserRepository.findAllVisible(any(PagedRequest.class), eq(selfId), eq(communityIds)))
                .thenReturn(new PagedResult<>(List.of(), 10, 0, 0, 0));

        // When
        getUserService.findAllVisible(PagedRequest.of(0, 10), UserScope.visibleTo(selfId, communityIds));

        // Then
        verify(getUserRepository).findAllVisible(any(PagedRequest.class), eq(selfId), eq(communityIds));
        verify(getUserRepository, never()).findAll(any(PagedRequest.class));
    }

    @Test
    void findAllVisible_sortsByNumber_whenTheRequestIsUnsorted() {
        // Given
        when(getUserRepository.findAllVisible(any(PagedRequest.class), any(), any()))
                .thenReturn(new PagedResult<>(List.of(), 10, 0, 0, 0));
        PagedRequest request = PagedRequest.of(0, 10);

        // When
        getUserService.findAllVisible(request, UserScope.visibleTo(UUID.randomUUID(), Set.of(UUID.randomUUID())));

        // Then
        assertEquals(1, request.getOrders().size());
        assertEquals("number", request.getOrders().get(0).getProperty());
        assertEquals(Direction.ASC, request.getOrders().get(0).getDirection());
    }

    @Test
    void findAllVisible_attachesEveryMembership_evenThoseOutsideTheScope() {
        // The capabilities are decided on a user's full memberships, so narrowing them is the
        // response's job, not the service's.
        UUID userId = UUID.randomUUID();
        User user = UserMother.randomUserWithId(userId);
        CommunityMembership inScope = randomMembership(user);
        CommunityMembership outOfScope = randomMembership(user);
        when(getUserRepository.findAllVisible(any(PagedRequest.class), any(), any()))
                .thenReturn(new PagedResult<>(List.of(user), 1, 1, 1, 0));
        when(getMembershipsRepository.findByUserIds(List.of(userId)))
                .thenReturn(Map.of(userId, List.of(inScope, outOfScope)));

        // When
        PagedResult<User> result = getUserService.findAllVisible(PagedRequest.of(0, 10),
                UserScope.visibleTo(UUID.randomUUID(), Set.of(inScope.getCommunity().getId())));

        // Then
        assertEquals(List.of(inScope, outOfScope), result.getItems().get(0).getMemberships());
        verify(getMembershipsRepository, times(1)).findByUserIds(List.of(userId));
    }

    private CommunityMembership randomMembership(User user) {
        Community community = CommunityMother.random().build();
        return new CommunityMembership.Builder()
                .withId(UUID.randomUUID())
                .withUser(user)
                .withCommunity(community)
                .withRole(CommunityRole.COMMUNITY_MEMBER)
                .withEnabled(true)
                .build();
    }
}
