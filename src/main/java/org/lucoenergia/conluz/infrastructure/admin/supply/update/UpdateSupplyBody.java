package org.lucoenergia.conluz.infrastructure.admin.supply.update;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;

import java.text.DateFormat;
import java.util.UUID;

@Schema(requiredProperties = {
        "code", "address", "partitionCoefficient"
})
public class UpdateSupplyBody {

    @NotEmpty
    private String code;
    private String name;
    @NotEmpty
    private String address;
    @Positive
    private Float partitionCoefficient;
    private Boolean enabled;
    private String validDateFrom;
    private String distributor;
    private String distributorCode;
    private Integer pointType;
    private String shellyMac;
    private String shellyId;
    private String shellyMqttPrefix;


    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Float getPartitionCoefficient() {
        return partitionCoefficient;
    }

    public void setPartitionCoefficient(Float partitionCoefficient) {
        this.partitionCoefficient = partitionCoefficient;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getValidDateFrom() {
        return validDateFrom;
    }

    public void setValidDateFrom(String validDateFrom) {
        this.validDateFrom = validDateFrom;
    }

    public String getDistributor() {
        return distributor;
    }

    public void setDistributor(String distributor) {
        this.distributor = distributor;
    }

    public String getDistributorCode() {
        return distributorCode;
    }

    public void setDistributorCode(String distributorCode) {
        this.distributorCode = distributorCode;
    }

    public Integer getPointType() {
        return pointType;
    }

    public void setPointType(Integer pointType) {
        this.pointType = pointType;
    }

    public String getShellyMac() {
        return shellyMac;
    }

    public void setShellyMac(String shellyMac) {
        this.shellyMac = shellyMac;
    }

    public String getShellyId() {
        return shellyId;
    }

    public void setShellyId(String shellyId) {
        this.shellyId = shellyId;
    }

    public String getShellyMqttPrefix() {
        return shellyMqttPrefix;
    }

    public void setShellyMqttPrefix(String shellyMqttPrefix) {
        this.shellyMqttPrefix = shellyMqttPrefix;
    }

    public Supply mapToSupply(UUID supplyId) {
        Supply.Builder builder = new Supply.Builder();
        builder.withId(supplyId)
                .withCode(code)
                .withName(name)
                .withAddress(address)
                .withPartitionCoefficient(partitionCoefficient)
                .withValidDateFrom(validDateFrom != null ? DateConverter.convertStringToLocalDate(validDateFrom, "yyyy-MM-dd") : null)
                .withDistributor(distributor)
                .withDistributorCode(distributorCode)
                .withPointType(pointType)
                .withShellyMac(shellyMac)
                .withShellyId(shellyId)
                .withShellyMqttPrefix(shellyMqttPrefix);
        return builder.build();
    }
}
