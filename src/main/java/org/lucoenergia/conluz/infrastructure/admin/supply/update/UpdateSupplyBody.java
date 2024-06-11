package org.lucoenergia.conluz.infrastructure.admin.supply.update;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;

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
    private String datadisValidDateFrom;
    private String datadisDistributor;
    private String datadisDistributorCode;
    private Integer datadisPointType;
    private Boolean datadisIsThirdParty;
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

    public String getDatadisValidDateFrom() {
        return datadisValidDateFrom;
    }

    public void setDatadisValidDateFrom(String datadisValidDateFrom) {
        this.datadisValidDateFrom = datadisValidDateFrom;
    }

    public String getDatadisDistributor() {
        return datadisDistributor;
    }

    public void setDatadisDistributor(String datadisDistributor) {
        this.datadisDistributor = datadisDistributor;
    }

    public String getDatadisDistributorCode() {
        return datadisDistributorCode;
    }

    public void setDatadisDistributorCode(String datadisDistributorCode) {
        this.datadisDistributorCode = datadisDistributorCode;
    }

    public Integer getDatadisPointType() {
        return datadisPointType;
    }

    public void setDatadisPointType(Integer datadisPointType) {
        this.datadisPointType = datadisPointType;
    }

    public Boolean getDatadisIsThirdParty() {
        return datadisIsThirdParty;
    }

    public void setDatadisIsThirdParty(Boolean datadisIsThirdParty) {
        this.datadisIsThirdParty = datadisIsThirdParty;
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
                .withDatadisValidDateFrom(datadisValidDateFrom != null ?
                        DateConverter.convertStringToLocalDate(datadisValidDateFrom, "yyyy-MM-dd") :
                        null)
                .withDatadisDistributor(datadisDistributor)
                .withDatadisDistributorCode(datadisDistributorCode)
                .withDatadisPointType(datadisPointType)
                .withDatadisIsThirdParty(datadisIsThirdParty)
                .withShellyMac(shellyMac)
                .withShellyId(shellyId)
                .withShellyMqttPrefix(shellyMqttPrefix);
        return builder.build();
    }
}
