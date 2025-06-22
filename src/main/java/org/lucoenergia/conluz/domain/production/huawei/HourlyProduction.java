package org.lucoenergia.conluz.domain.production.huawei;

import java.time.Instant;

public class HourlyProduction {

    private Instant time;
    private String stationCode;
    /**
     * Global irradiation in kWh/m²
     */
    private Double radiationIntensity;
    /**
     * Theoretical yield in kWh
     */
    private Double theoryPower;
    /**
     * Inverter yield in kWh
     */
    private Double inverterPower;
    /**
     * Feed-in energy in kWh
     */
    private Double ongridPower;
    /**
     * Revenue.
     * The currency specified in the management system
     */
    private Double powerProfit;

    public Instant getTime() {
        return time;
    }

    public String getStationCode() {
        return stationCode;
    }

    public Double getRadiationIntensity() {
        return radiationIntensity;
    }

    public Double getTheoryPower() {
        return theoryPower;
    }

    public Double getInverterPower() {
        return inverterPower;
    }

    public Double getOngridPower() {
        return ongridPower;
    }

    public Double getPowerProfit() {
        return powerProfit;
    }


    public static class Builder {
        private Instant time;
        private Double radiationIntensity;
        private Double theoryPower;
        private Double inverterPower;
        private Double ongridPower;
        private Double powerProfit;
        private String stationCode;

        public Builder withTime(Instant time) {
            this.time = time;
            return this;
        }

        public Builder withRadiationIntensity(Double radiationIntensity) {
            this.radiationIntensity = radiationIntensity;
            return this;
        }

        public Builder withTheoryPower(Double theoryPower) {
            this.theoryPower = theoryPower;
            return this;
        }

        public Builder withInverterPower(Double inverterPower) {
            this.inverterPower = inverterPower;
            return this;
        }

        public Builder withOngridPower(Double ongridPower) {
            this.ongridPower = ongridPower;
            return this;
        }

        public Builder withPowerProfit(Double powerProfit) {
            this.powerProfit = powerProfit;
            return this;
        }

        public Builder withStationCode(String stationCode) {
            this.stationCode = stationCode;
            return this;
        }

        public HourlyProduction build() {
            HourlyProduction hourlyProduction = new HourlyProduction();
            hourlyProduction.time = this.time;
            hourlyProduction.radiationIntensity = this.radiationIntensity;
            hourlyProduction.theoryPower = this.theoryPower;
            hourlyProduction.inverterPower = this.inverterPower;
            hourlyProduction.ongridPower = this.ongridPower;
            hourlyProduction.powerProfit = this.powerProfit;
            hourlyProduction.stationCode = this.stationCode;
            return hourlyProduction;
        }
    }
}
