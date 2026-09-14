package org.lucoenergia.conluz.infrastructure.production.sharingagreement.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.update.UpdateSharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.update.UpdateSharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.update.UpdateSharingAgreementServiceImpl;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateSharingAgreementServiceImplTest {

    @Mock
    private UpdateSharingAgreementRepository repository;

    private UpdateSharingAgreementServiceImpl service() {
        return new UpdateSharingAgreementServiceImpl(repository);
    }

    private UpdateSharingAgreement anUpdate() {
        return new UpdateSharingAgreement.Builder()
                .withName("name")
                .withNotes("notes")
                .withInstalledPowerKw(BigDecimal.TEN)
                .withUpdatedBy(UUID.randomUUID())
                .build();
    }

    @ParameterizedTest
    @EnumSource(SharingAgreementStatus.class)
    void update_delegatesToRepository_regardlessOfStatus(SharingAgreementStatus status) {
        UUID plantId = UUID.randomUUID();
        UUID agreementId = UUID.randomUUID();
        UpdateSharingAgreement update = anUpdate();
        SharingAgreement updated = new SharingAgreement.Builder().withId(agreementId).withStatus(status).build();
        when(repository.update(plantId, agreementId, update)).thenReturn(updated);

        SharingAgreement result = service().update(plantId, agreementId, update);

        assertEquals(updated, result);
        verify(repository).update(plantId, agreementId, update);
    }
}
