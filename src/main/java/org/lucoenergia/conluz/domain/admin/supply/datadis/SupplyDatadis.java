package org.lucoenergia.conluz.domain.admin.supply.datadis;

public class SupplyDatadis {

    private final Boolean thirdParty;

    private SupplyDatadis(Builder builder) {
        this.thirdParty = builder.thirdParty;
    }

    public static class Builder {
        private Boolean thirdParty;

        public Builder withThirdParty(Boolean thirdParty) {
            this.thirdParty = thirdParty;
            return this;
        }

        public SupplyDatadis build() {
            return new SupplyDatadis(this);
        }
    }

    public Boolean isThirdParty() {
        return thirdParty;
    }
}
