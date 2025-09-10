package org.lucoenergia.conluz.infrastructure.admin.supply;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.infrastructure.admin.user.UserResponse;

import java.time.LocalDate;
import java.util.UUID;

public class SupplyResponse {

    @Schema(description = "Internal unique identifier of the supply", example = "ebbe60d1-f9db-455c-8c2d-c34ae7a1c23c")
    private final UUID id;
    @Schema(description = "Code that identifies the supply", example = "ES0031300119158001DL0Y")
    private final String code;
    @Schema(description = "Owner of the supply")
    private final UserResponse user;
    @Schema(description = "Name of the supply", example = "My house")
    private final String name;
    @Schema(description = "Address of the supply", example = "Fake Street 123")
    private final String address;
    @Schema(description = "Reference ID of the address", example = "4ASDF654ASDF89ASD")
    private final String addressRef;
    @Schema(description = "Address of the supply", example = "2.403561")
    private final Float partitionCoefficient;
    @Schema(description = "Whether the supply is enabled or disabled", example = "true")
    private final Boolean enabled;
    @Schema(description = "Date on which the supply point was registered as valid", example = "true")
    private final LocalDate datadisValidDateFrom;
    @Schema(description = "Name of the distribution company", example = "Endesa")
    private final String datadisDistributor;
    @Schema(description = "Code of the distribution company", example = "2")
    private final String datadisDistributorCode;
    @Schema(description = "Type of measurement point", example = "3")
    private final Integer datadisPointType;
    @Schema(description = "Whether is an authorized third parth supply or not", example = "true")
    private final Boolean datadisIsThirdParty;
    @Schema(description = "MAC address of the Shelly", example = "24:4c:ab:41:99:f6")
    private final String shellyMac;
    @Schema(description = "Unique identifier of the Shelly", example = "shellyem-244CAB4199F6")
    private final String shellyId;
    @Schema(description = "MQTT prefix for the Shelly", example = "70u590f396zbae/johndoe")
    private final String shellyMqttPrefix;


    public SupplyResponse(Supply supply) {
        this.id = supply.getId();
        this.code = supply.getCode();
        this.name = supply.getName();
        this.address = supply.getAddress();
        this.addressRef = supply.getAddressRef();
        this.partitionCoefficient = supply.getPartitionCoefficient();
        this.enabled = supply.getEnabled();
        this.user = supply.getUser() != null ? new UserResponse(supply.getUser()) : null;
        this.datadisIsThirdParty = supply.isThirdParty();
        this.datadisValidDateFrom = supply.getValidDateFrom();
        this.datadisDistributor = supply.getDistributor();
        this.datadisDistributorCode = supply.getDistributorCode();
        this.datadisPointType = supply.getPointType();
        this.shellyMac = supply.getShellyMac();
        this.shellyId = supply.getShellyId();
        this.shellyMqttPrefix = supply.getShellyMqttPrefix();
    }

    public UUID getId() {
        return id;
    }
    public String getCode() {
        return code;
    }

    public UserResponse getUser() {
        return user;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getAddressRef() {
        return addressRef;
    }

    public Float getPartitionCoefficient() {
        return partitionCoefficient;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public LocalDate getDatadisValidDateFrom() {
        return datadisValidDateFrom;
    }

    public String getDatadisDistributor() {
        return datadisDistributor;
    }

    public String getDatadisDistributorCode() {
        return datadisDistributorCode;
    }

    public Integer getDatadisPointType() {
        return datadisPointType;
    }

    public Boolean getDatadisIsThirdParty() {
        return datadisIsThirdParty;
    }

    public String getShellyMac() {
        return shellyMac;
    }

    public String getShellyId() {
        return shellyId;
    }

    public String getShellyMqttPrefix() {
        return shellyMqttPrefix;
    }
}
