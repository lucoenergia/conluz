package org.lucoenergia.conluz.domain.admin.supply.contract;

import java.time.LocalDate;

public class SupplyContract {

    private LocalDate validDateFrom;

    public SupplyContract() {
    }

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

    public void setValidDateFrom(LocalDate validDateFrom) {
        this.validDateFrom = validDateFrom;
    }
}
