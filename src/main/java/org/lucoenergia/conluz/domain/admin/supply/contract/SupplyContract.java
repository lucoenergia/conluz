package org.lucoenergia.conluz.domain.admin.supply.contract;

import java.time.LocalDate;

public class SupplyContract {

    private final LocalDate validDateFrom;

    private SupplyContract(Builder builder) {
        this.validDateFrom = builder.validDateFrom;
    }

    public static class Builder {
        private LocalDate validDateFrom;

        public Builder withValidDateFrom(LocalDate validDateFrom) {
            this.validDateFrom = validDateFrom;
            return this;
        }

        public SupplyContract build() {
            return new SupplyContract(this);
        }
    }

    public LocalDate getValidDateFrom() {
        return validDateFrom;
    }
}
