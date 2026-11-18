package org.lucoenergia.conluz.infrastructure.production.sharingagreement;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.SharingAgreementPlantMismatchException;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorCode;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorDetail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An agreement that belongs to another plant is answered exactly as a missing one. Every path raising
 * {@link SharingAgreementPlantMismatchException} sits behind a guard that answers 404 first, so it is exercised
 * here through the handler rather than over HTTP.
 */
class SharingAgreementExceptionHandlerTest extends BaseIntegrationTest {

    @Autowired
    private SharingAgreementExceptionHandler handler;

    @Test
    void anAgreementMissingIsNotFoundWithItsCode() {
        UUID agreementId = UUID.randomUUID();

        ResponseEntity<RestError> response = handler.handleException(new SharingAgreementNotFoundException(agreementId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getErrors()).extracting(RestErrorDetail::getCode)
                .containsExactly(RestErrorCode.SHARING_AGREEMENT_NOT_FOUND);
    }

    @Test
    void anAgreementOfAnotherPlantIsAnsweredExactlyAsAMissingOne() {
        UUID agreementId = UUID.randomUUID();

        ResponseEntity<RestError> mismatch = handler.handleException(
                new SharingAgreementPlantMismatchException(agreementId, UUID.randomUUID()));
        ResponseEntity<RestError> missing = handler.handleException(new SharingAgreementNotFoundException(agreementId));

        assertThat(mismatch.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(mismatch.getBody().getMessage()).isEqualTo(missing.getBody().getMessage());
        assertThat(mismatch.getBody().getErrors()).extracting(RestErrorDetail::getCode, RestErrorDetail::getMessage,
                        RestErrorDetail::getParams)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(RestErrorCode.SHARING_AGREEMENT_NOT_FOUND,
                        missing.getBody().getMessage(), null));
    }
}
