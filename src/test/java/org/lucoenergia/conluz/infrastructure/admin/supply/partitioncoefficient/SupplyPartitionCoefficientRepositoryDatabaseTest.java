package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionCoefficientJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;

@Transactional
class SupplyPartitionCoefficientRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private SupplyPartitionCoefficientRepository repository;

    @Autowired
    private SupplyPartitionCoefficientJpaRepository jpaRepository;

    @Autowired
    private SupplyRepository supplyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommunityJpaRepository communityJpaRepository;

    private SupplyEntity persistSupply() {
        UserEntity user = UserMother.randomUserEntity();
        userRepository.save(user);
        return supplyRepository.save(SupplyEntityMother.random(
                user,
                communityJpaRepository.getReferenceById(DEFAULT_COMMUNITY_ID)
        ));
    }

    private SupplyPartitionCoefficient persist(UUID supplyId, BigDecimal coefficient,
                                               Instant validFrom, Instant validTo) {
        return repository.save(new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supplyId)
                .withCoefficient(coefficient)
                .withValidFrom(validFrom)
                .withValidTo(validTo)
                .withCreatedAt(Instant.now())
                .build());
    }

    @Test
    void findActiveBySupplyIdReturnsRowWithNullValidTo() {
        SupplyEntity supply = persistSupply();
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        persist(supply.getId(), BigDecimal.valueOf(1.000000), t0, t1);
        persist(supply.getId(), BigDecimal.valueOf(2.000000), t1, null);

        Optional<SupplyPartitionCoefficient> result = repository.findActiveBySupplyId(supply.getId());

        assertTrue(result.isPresent());
        assertEquals(0, BigDecimal.valueOf(2.000000).compareTo(result.get().getCoefficient()));
        assertNull(result.get().getValidTo());
    }

    @Test
    void findBySupplyIdAtTimestampRespectsInclusiveLowerBound() {
        SupplyEntity supply = persistSupply();
        Instant changeAt = Instant.parse("2025-03-01T00:00:00Z");
        persist(supply.getId(), BigDecimal.valueOf(3.000000), Instant.parse("2024-01-01T00:00:00Z"), changeAt);
        persist(supply.getId(), BigDecimal.valueOf(4.000000), changeAt, null);

        // Query at the exact change time should return the new period (valid_from inclusive)
        Optional<SupplyPartitionCoefficient> result = repository.findBySupplyIdAtTimestamp(supply.getId(), changeAt);

        assertTrue(result.isPresent());
        assertEquals(0, BigDecimal.valueOf(4.000000).compareTo(result.get().getCoefficient()));
    }

    @Test
    void findBySupplyIdInRangeReturnsOverlappingPeriods() {
        SupplyEntity supply = persistSupply();
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2025-06-01T00:00:00Z");
        persist(supply.getId(), BigDecimal.valueOf(1.000000), t0, t1);
        persist(supply.getId(), BigDecimal.valueOf(2.000000), t1, null);

        List<SupplyPartitionCoefficient> result = repository.findBySupplyIdInRange(
                supply.getId(), Instant.parse("2024-06-01T00:00:00Z"), t2);

        assertEquals(2, result.size());
    }

    @Test
    void closeActivePeriodSetsValidToOnOpenRow() {
        SupplyEntity supply = persistSupply();
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        persist(supply.getId(), BigDecimal.valueOf(5.000000), start, null);

        Instant closeAt = Instant.parse("2025-01-01T00:00:00Z");
        repository.closeActivePeriod(supply.getId(), closeAt);

        Optional<SupplyPartitionCoefficient> active = repository.findActiveBySupplyId(supply.getId());
        assertFalse(active.isPresent());

        List<SupplyPartitionCoefficient> history = repository.findAllBySupplyIdOrderByValidFromAsc(supply.getId());
        assertEquals(1, history.size());
        assertEquals(closeAt, history.get(0).getValidTo());
    }

    @Test
    void uniqueActiveConstraintPreventsSecondOpenRowForSameSupply() {
        SupplyEntity supply = persistSupply();
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        persist(supply.getId(), BigDecimal.valueOf(3.000000), t0, null);

        // Attempting to persist a second open-ended row for the same supply must fail
        assertThrows(Exception.class, () -> {
            persist(supply.getId(), BigDecimal.valueOf(4.000000), t1, null);
            // Force the flush so the DB constraint fires within this transaction
            jpaRepository.flush();
        });
    }

    @Test
    void findAllActiveAtTimestampReturnsOnlyActiveRows() {
        SupplyEntity supply1 = persistSupply();
        SupplyEntity supply2 = persistSupply();

        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        Instant queryAt = Instant.parse("2025-06-01T00:00:00Z");

        persist(supply1.getId(), BigDecimal.valueOf(10.0), t0, null);
        persist(supply2.getId(), BigDecimal.valueOf(20.0), t0, null);
        // closed row — should not appear
        persist(persistSupply().getId(), BigDecimal.valueOf(30.0), t0, t1);

        List<SupplyPartitionCoefficient> result = repository.findAllActiveAtTimestamp(queryAt);

        assertTrue(result.stream().anyMatch(c -> c.getSupplyId().equals(supply1.getId())));
        assertTrue(result.stream().anyMatch(c -> c.getSupplyId().equals(supply2.getId())));
        assertTrue(result.stream().noneMatch(c -> c.getValidTo() != null && c.getValidTo().isBefore(queryAt)));
    }

    @Test
    void findAllBySupplyIdOrderByValidFromAscReturnsChronologicalHistory() {
        SupplyEntity supply = persistSupply();
        Instant t0 = Instant.parse("2023-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2025-01-01T00:00:00Z");
        persist(supply.getId(), BigDecimal.valueOf(1.000000), t0, t1);
        persist(supply.getId(), BigDecimal.valueOf(2.000000), t1, t2);
        persist(supply.getId(), BigDecimal.valueOf(3.000000), t2, null);

        List<SupplyPartitionCoefficient> history = repository.findAllBySupplyIdOrderByValidFromAsc(supply.getId());

        assertEquals(3, history.size());
        assertEquals(t0, history.get(0).getValidFrom());
        assertEquals(t1, history.get(1).getValidFrom());
        assertEquals(t2, history.get(2).getValidFrom());
    }
}
