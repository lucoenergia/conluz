package org.lucoenergia.conluz.infrastructure.admin.supply.transfer;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.transfer.CreateSupplyOwnershipTransferRepository;
import org.lucoenergia.conluz.domain.admin.supply.transfer.SupplyOwnershipTransfer;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.lucoenergia.conluz.domain.admin.community.CommunityMother.DEFAULT_COMMUNITY_ID;

@Transactional
class CreateSupplyOwnershipTransferRepositoryDatabaseTest extends BaseIntegrationTest {

    private static final LocalDate EFFECTIVE_DATE = LocalDate.of(2026, 3, 15);
    private static final Instant TRANSFERRED_AT = Instant.parse("2026-03-20T09:30:00Z");

    @Autowired
    private CreateSupplyOwnershipTransferRepository repository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    @Test
    void storesEveryFieldOfTheRecord() {
        User previous = createUserRepository.create(UserMother.randomUser());
        User next = createUserRepository.create(UserMother.randomUser());
        User admin = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(SupplyMother.random(previous).build(),
                UserId.of(previous.getId()), DEFAULT_COMMUNITY_ID);
        UUID id = UUID.randomUUID();

        SupplyOwnershipTransfer created = repository.create(transfer(id, supply, UserId.of(previous.getId()),
                next, admin));
        entityManager.flush();
        entityManager.clear();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM supply_ownership_transfers WHERE id = ?", id);
        assertEquals(supply.getId(), row.get("supply_id"));
        assertEquals(previous.getId(), row.get("previous_user_id"));
        assertEquals(next.getId(), row.get("new_user_id"));
        assertEquals(Date.valueOf(EFFECTIVE_DATE), row.get("effective_date"));
        assertEquals(true, row.get("history_included"));
        assertEquals(admin.getId(), row.get("transferred_by"));
        assertEquals(TRANSFERRED_AT, ((Timestamp) row.get("transferred_at")).toInstant());
        assertEquals(id, created.getId());
    }

    @Test
    void storesNoPreviousOwnerForASupplyThatHadNone() {
        User owner = createUserRepository.create(UserMother.randomUser());
        User next = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(SupplyMother.random(owner).build(),
                UserId.of(owner.getId()), DEFAULT_COMMUNITY_ID);
        UUID id = UUID.randomUUID();

        repository.create(transfer(id, supply, null, next, owner));
        entityManager.flush();

        assertNull(jdbcTemplate.queryForObject(
                "SELECT previous_user_id FROM supply_ownership_transfers WHERE id = ?", UUID.class, id));
    }

    private static SupplyOwnershipTransfer transfer(UUID id, Supply supply, UserId previous, User next,
                                                    User admin) {
        return new SupplyOwnershipTransfer.Builder()
                .withId(id)
                .withSupplyId(SupplyId.of(supply.getId()))
                .withPreviousOwnerId(previous)
                .withNewOwnerId(UserId.of(next.getId()))
                .withEffectiveDate(EFFECTIVE_DATE)
                .withHistoryIncluded(true)
                .withTransferredBy(UserId.of(admin.getId()))
                .withTransferredAt(TRANSFERRED_AT)
                .build();
    }
}
