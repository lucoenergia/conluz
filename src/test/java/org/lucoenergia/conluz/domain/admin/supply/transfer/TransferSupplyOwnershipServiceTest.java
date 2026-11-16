package org.lucoenergia.conluz.domain.admin.supply.transfer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.lock.LockCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.SupplyOwnerNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.lock.LockSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.admin.supply.transfer.TransferSupplyOwnershipServiceImpl;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransferSupplyOwnershipServiceTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    /** 11:00 in Madrid on 2026-03-16. */
    private static final Instant NOW = Instant.parse("2026-03-16T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 16);
    private static final UserId ADMIN = UserId.of(UUID.randomUUID());
    /** Ordered as PostgreSQL orders UUIDs, by their text form; {@link UUID#compareTo} would order them the other way. */
    private static final UUID LOWER = UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff");
    private static final UUID HIGHER = UUID.fromString("80000000-0000-0000-0000-000000000000");

    @Mock
    private GetSupplyRepository getSupplyRepository;
    @Mock
    private GetUserRepository getUserRepository;
    @Mock
    private GetMembershipsRepository getMembershipsRepository;
    @Mock
    private GetSupplyOwnershipTransferRepository getTransferRepository;
    @Mock
    private LockCommunityRepository lockCommunityRepository;
    @Mock
    private LockUserRepository lockUserRepository;
    @Mock
    private LockSupplyRepository lockSupplyRepository;
    @Mock
    private UpdateSupplyOwnerRepository updateSupplyOwnerRepository;
    @Mock
    private CreateSupplyOwnershipTransferRepository createTransferRepository;
    @Mock
    private ZoneResolver zoneResolver;

    private UUID communityId;

    @BeforeEach
    void givenEveryLockSucceeds() {
        communityId = UUID.randomUUID();
        when(zoneResolver.resolveZoneIdForCommunity(communityId)).thenReturn(MADRID);
        when(lockCommunityRepository.lock(communityId)).thenReturn(true);
        when(lockUserRepository.lock(any())).thenReturn(true);
        when(lockSupplyRepository.lockAsReferenced(any())).thenReturn(true);
        when(updateSupplyOwnerRepository.updateOwner(any(), any())).thenReturn(true);
        when(getTransferRepository.findLatestEffectiveDate(any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("SUP-009 the community, then both owners' user rows in ascending id order, then the supply are "
            + "locked, before the membership check and the write")
    void locksInTheDocumentedOrder() {
        User previous = user(HIGHER);
        User next = user(LOWER);
        Supply supply = supplyOwnedBy(previous);
        givenAMember(next);

        service().transfer(SupplyId.of(supply.getId()), UserPersonalId.of(next.getPersonalId()), TODAY, ADMIN);

        InOrder order = inOrder(lockCommunityRepository, lockUserRepository, getMembershipsRepository,
                lockSupplyRepository, updateSupplyOwnerRepository, createTransferRepository);
        order.verify(lockCommunityRepository).lock(communityId);
        order.verify(lockUserRepository).lock(UserId.of(LOWER));
        order.verify(lockUserRepository).lock(UserId.of(HIGHER));
        order.verify(getMembershipsRepository).findByUserIdAndCommunityId(LOWER, communityId);
        order.verify(lockSupplyRepository).lockAsReferenced(SupplyId.of(supply.getId()));
        order.verify(updateSupplyOwnerRepository).updateOwner(SupplyId.of(supply.getId()), UserId.of(LOWER));
        order.verify(createTransferRepository).create(any());
    }

    @Test
    @DisplayName("SUP-009 the user rows are locked in ascending id order whichever of the two is the new owner")
    void locksTheUsersInAscendingOrder_whenTheNewOwnerHasTheHigherId() {
        User previous = user(LOWER);
        User next = user(HIGHER);
        Supply supply = supplyOwnedBy(previous);
        givenAMember(next);

        service().transfer(SupplyId.of(supply.getId()), UserPersonalId.of(next.getPersonalId()), TODAY, ADMIN);

        InOrder order = inOrder(lockUserRepository);
        order.verify(lockUserRepository).lock(UserId.of(LOWER));
        order.verify(lockUserRepository).lock(UserId.of(HIGHER));
    }

    @Test
    @DisplayName("SUP-011 the record carries both owners, the date, the history included, the caller and the "
            + "clock's instant")
    void recordsTheTransfer() {
        User previous = user(UUID.randomUUID());
        User next = user(UUID.randomUUID());
        Supply supply = supplyOwnedBy(previous);
        givenAMember(next);

        service().transfer(SupplyId.of(supply.getId()), UserPersonalId.of(next.getPersonalId()),
                TODAY.minusDays(3), ADMIN);

        ArgumentCaptor<SupplyOwnershipTransfer> captor = ArgumentCaptor.forClass(SupplyOwnershipTransfer.class);
        verify(createTransferRepository).create(captor.capture());
        SupplyOwnershipTransfer record = captor.getValue();
        assertThat(record.getId()).isNotNull();
        assertThat(record.getSupplyId()).isEqualTo(SupplyId.of(supply.getId()));
        assertThat(record.getPreviousOwnerId()).contains(UserId.of(previous.getId()));
        assertThat(record.getNewOwnerId()).isEqualTo(UserId.of(next.getId()));
        assertThat(record.getEffectiveDate()).isEqualTo(TODAY.minusDays(3));
        assertThat(record.isHistoryIncluded()).isTrue();
        assertThat(record.getTransferredBy()).isEqualTo(ADMIN);
        assertThat(record.getTransferredAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("SUP-011 a supply without an owner locks only the new owner's row and records no previous owner")
    void aSupplyWithoutAnOwnerLocksOnlyTheNewOwner() {
        User next = user(UUID.randomUUID());
        Supply supply = supplyOwnedBy(null);
        givenAMember(next);

        service().transfer(SupplyId.of(supply.getId()), UserPersonalId.of(next.getPersonalId()), TODAY, ADMIN);

        verify(lockUserRepository).lock(UserId.of(next.getId()));
        verify(lockUserRepository, times(1)).lock(any());
        ArgumentCaptor<SupplyOwnershipTransfer> captor = ArgumentCaptor.forClass(SupplyOwnershipTransfer.class);
        verify(createTransferRepository).create(captor.capture());
        assertThat(captor.getValue().getPreviousOwnerId()).isEmpty();
    }

    @Test
    @DisplayName("SUP-009 an unknown personal ID is refused before any user row is locked, and nothing is written")
    void anUnknownPersonalIdIsRefused() {
        Supply supply = supplyOwnedBy(user(UUID.randomUUID()));
        when(getUserRepository.findByPersonalId(any())).thenReturn(Optional.empty());

        assertThrows(SupplyOwnerNotFoundException.class, () -> service().transfer(SupplyId.of(supply.getId()),
                UserPersonalId.of("12345678Z"), TODAY, ADMIN));

        verifyNoInteractions(lockUserRepository, lockSupplyRepository);
        assertNothingWritten();
    }

    @Test
    @DisplayName("SUP-009 a new owner whose row is gone once locked is refused like an unknown one")
    void aNewOwnerDeletedBeforeTheLockIsRefused() {
        User next = user(UUID.randomUUID());
        Supply supply = supplyOwnedBy(user(UUID.randomUUID()));
        givenAMember(next);
        when(lockUserRepository.lock(UserId.of(next.getId()))).thenReturn(false);

        assertThrows(SupplyOwnerNotFoundException.class, () -> service().transfer(SupplyId.of(supply.getId()),
                UserPersonalId.of(next.getPersonalId()), TODAY, ADMIN));

        verify(getMembershipsRepository, never()).findByUserIdAndCommunityId(any(), any());
        assertNothingWritten();
    }

    @Test
    @DisplayName("SUP-009 a new owner without a membership in the supply's community is refused like an unknown one")
    void aNonMemberIsRefused() {
        User next = user(UUID.randomUUID());
        Supply supply = supplyOwnedBy(user(UUID.randomUUID()));
        when(getUserRepository.findByPersonalId(personalId(next)))
                .thenReturn(Optional.of(next));
        when(getMembershipsRepository.findByUserIdAndCommunityId(next.getId(), communityId))
                .thenReturn(Optional.empty());

        assertThrows(SupplyOwnerNotFoundException.class, () -> service().transfer(SupplyId.of(supply.getId()),
                UserPersonalId.of(next.getPersonalId()), TODAY, ADMIN));

        verifyNoInteractions(lockSupplyRepository);
        assertNothingWritten();
    }

    @Test
    @DisplayName("SUP-009 a supply deleted before it is locked is answered not found, and nothing is written")
    void aSupplyDeletedBeforeTheLockIsNotFound() {
        User next = user(UUID.randomUUID());
        Supply supply = supplyOwnedBy(user(UUID.randomUUID()));
        givenAMember(next);
        when(lockSupplyRepository.lockAsReferenced(SupplyId.of(supply.getId()))).thenReturn(false);

        assertThrows(SupplyNotFoundException.class, () -> service().transfer(SupplyId.of(supply.getId()),
                UserPersonalId.of(next.getPersonalId()), TODAY, ADMIN));

        assertNothingWritten();
    }

    @Test
    @DisplayName("SUP-009 the current owner is refused, and nothing is written")
    void theCurrentOwnerIsRefused() {
        User owner = user(UUID.randomUUID());
        Supply supply = supplyOwnedBy(owner);
        givenAMember(owner);

        assertThrows(SupplyAlreadyOwnedByUserException.class, () -> service().transfer(
                SupplyId.of(supply.getId()), UserPersonalId.of(owner.getPersonalId()), TODAY, ADMIN));

        assertNothingWritten();
    }

    @Test
    @DisplayName("SUP-010 today in the community's time zone is accepted, the day after is refused")
    void theDateIsComparedWithTodayInTheCommunitysZone() {
        User next = user(UUID.randomUUID());
        Supply supply = supplyOwnedBy(user(UUID.randomUUID()));
        givenAMember(next);
        // 00:30 on 2026-03-17 in Madrid, still 2026-03-16 in UTC
        TransferSupplyOwnershipService service = service(Clock.fixed(Instant.parse("2026-03-16T23:30:00Z"),
                ZoneOffset.UTC));

        assertThrows(SupplyTransferDateInFutureException.class, () -> service.transfer(SupplyId.of(supply.getId()),
                UserPersonalId.of(next.getPersonalId()), LocalDate.of(2026, 3, 18), ADMIN));
        assertNothingWritten();

        service.transfer(SupplyId.of(supply.getId()), UserPersonalId.of(next.getPersonalId()),
                LocalDate.of(2026, 3, 17), ADMIN);
        verify(updateSupplyOwnerRepository).updateOwner(SupplyId.of(supply.getId()), UserId.of(next.getId()));
    }

    @Test
    @DisplayName("SUP-010 a date on or before the previous transfer's is refused, the day after is accepted")
    void theDateMustBeAfterThePreviousTransfers() {
        User next = user(UUID.randomUUID());
        Supply supply = supplyOwnedBy(user(UUID.randomUUID()));
        givenAMember(next);
        LocalDate previous = TODAY.minusDays(5);
        when(getTransferRepository.findLatestEffectiveDate(SupplyId.of(supply.getId())))
                .thenReturn(Optional.of(previous));

        assertThrows(SupplyTransferDateNotAfterPreviousException.class, () -> service().transfer(
                SupplyId.of(supply.getId()), UserPersonalId.of(next.getPersonalId()), previous, ADMIN));
        assertThrows(SupplyTransferDateNotAfterPreviousException.class, () -> service().transfer(
                SupplyId.of(supply.getId()), UserPersonalId.of(next.getPersonalId()), previous.minusDays(1), ADMIN));
        assertNothingWritten();

        service().transfer(SupplyId.of(supply.getId()), UserPersonalId.of(next.getPersonalId()),
                previous.plusDays(1), ADMIN);
        verify(updateSupplyOwnerRepository).updateOwner(SupplyId.of(supply.getId()), UserId.of(next.getId()));
    }

    @Test
    void returnsTheSupplyReadAfterTheWrite() {
        User next = user(UUID.randomUUID());
        Supply supply = supplyOwnedBy(user(UUID.randomUUID()));
        givenAMember(next);
        Supply transferred = SupplyMother.random(next).withId(supply.getId())
                .withCommunity(supply.getCommunity()).build();
        when(getSupplyRepository.findById(SupplyId.of(supply.getId())))
                .thenReturn(Optional.of(supply), Optional.of(supply), Optional.of(transferred));

        Supply result = service().transfer(SupplyId.of(supply.getId()), UserPersonalId.of(next.getPersonalId()),
                TODAY, ADMIN);

        assertThat(result).isSameAs(transferred);
    }

    // --- Helpers ---

    private TransferSupplyOwnershipService service() {
        return service(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private TransferSupplyOwnershipService service(Clock clock) {
        return new TransferSupplyOwnershipServiceImpl(getSupplyRepository, getUserRepository,
                getMembershipsRepository, getTransferRepository, lockCommunityRepository, lockUserRepository,
                lockSupplyRepository, updateSupplyOwnerRepository, createTransferRepository, zoneResolver, clock);
    }

    private static User user(UUID id) {
        return UserMother.randomUserWithId(id);
    }

    private Supply supplyOwnedBy(User owner) {
        Supply supply = SupplyMother.random(owner)
                .withCommunity(CommunityMother.random().withId(communityId).build())
                .build();
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));
        return supply;
    }

    private void givenAMember(User user) {
        when(getUserRepository.findByPersonalId(personalId(user)))
                .thenReturn(Optional.of(user));
        when(getMembershipsRepository.findByUserIdAndCommunityId(user.getId(), communityId))
                .thenReturn(Optional.of(mock(CommunityMembership.class)));
    }

    private static UserPersonalId personalId(User user) {
        return argThat(id -> id != null && user.getPersonalId().equals(id.getPersonalId()));
    }

    private void assertNothingWritten() {
        verify(updateSupplyOwnerRepository, never()).updateOwner(any(), any());
        verify(createTransferRepository, never()).create(any());
    }
}
