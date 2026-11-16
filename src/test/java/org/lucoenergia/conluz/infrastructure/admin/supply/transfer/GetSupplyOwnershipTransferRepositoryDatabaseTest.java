package org.lucoenergia.conluz.infrastructure.admin.supply.transfer;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.transfer.CreateSupplyOwnershipTransferRepository;
import org.lucoenergia.conluz.domain.admin.supply.transfer.GetSupplyOwnershipTransferRepository;
import org.lucoenergia.conluz.domain.admin.supply.transfer.SupplyOwnershipTransfer;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lucoenergia.conluz.domain.admin.community.CommunityMother.DEFAULT_COMMUNITY_ID;

@Transactional
class GetSupplyOwnershipTransferRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private GetSupplyOwnershipTransferRepository repository;
    @Autowired
    private CreateSupplyOwnershipTransferRepository createRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;

    @Test
    void theLatestEffectiveDateIsTheLatestAmongTheSupplysRecords_whateverTheOrderTheyWereWritten() {
        User owner = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupply(owner);
        Supply other = createSupply(owner);
        record(supply, owner, LocalDate.of(2025, 1, 10));
        record(supply, owner, LocalDate.of(2025, 9, 1));
        record(supply, owner, LocalDate.of(2025, 4, 20));
        record(other, owner, LocalDate.of(2026, 2, 1));

        Optional<LocalDate> latest = repository.findLatestEffectiveDate(SupplyId.of(supply.getId()));

        assertEquals(Optional.of(LocalDate.of(2025, 9, 1)), latest);
    }

    @Test
    void aSupplyNeverTransferredHasNoLatestEffectiveDate_evenWhenOtherSuppliesHaveRecords() {
        User owner = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupply(owner);
        Supply other = createSupply(owner);
        record(other, owner, LocalDate.of(2025, 1, 10));
        record(other, owner, LocalDate.of(2025, 9, 1));

        assertEquals(Optional.empty(), repository.findLatestEffectiveDate(SupplyId.of(supply.getId())));
    }

    private Supply createSupply(User owner) {
        return createSupplyRepository.create(SupplyMother.random(owner).build(), UserId.of(owner.getId()),
                DEFAULT_COMMUNITY_ID);
    }

    private void record(Supply supply, User user, LocalDate effectiveDate) {
        createRepository.create(new SupplyOwnershipTransfer.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(SupplyId.of(supply.getId()))
                .withPreviousOwnerId(UserId.of(user.getId()))
                .withNewOwnerId(UserId.of(user.getId()))
                .withEffectiveDate(effectiveDate)
                .withHistoryIncluded(true)
                .withTransferredBy(UserId.of(user.getId()))
                .withTransferredAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build());
    }
}
