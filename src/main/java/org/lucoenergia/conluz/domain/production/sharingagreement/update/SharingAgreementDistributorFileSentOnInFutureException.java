package org.lucoenergia.conluz.domain.production.sharingagreement.update;

import java.time.LocalDate;

/**
 * Raised when a sharing agreement's distributor file sent date is after today in the application
 * time zone: a file cannot have been sent on a day that has not come yet.
 */
public class SharingAgreementDistributorFileSentOnInFutureException extends RuntimeException {

    private final LocalDate distributorFileSentOn;
    private final LocalDate today;

    public SharingAgreementDistributorFileSentOnInFutureException(LocalDate distributorFileSentOn, LocalDate today) {
        this.distributorFileSentOn = distributorFileSentOn;
        this.today = today;
    }

    public LocalDate getDistributorFileSentOn() {
        return distributorFileSentOn;
    }

    public LocalDate getToday() {
        return today;
    }
}
