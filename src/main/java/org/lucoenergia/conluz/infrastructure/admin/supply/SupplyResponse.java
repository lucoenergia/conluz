package org.lucoenergia.conluz.infrastructure.admin.supply;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.contract.SupplyContract;
import org.lucoenergia.conluz.domain.admin.supply.datadis.SupplyDatadis;
import org.lucoenergia.conluz.domain.admin.supply.distributor.SupplyDistributor;
import org.lucoenergia.conluz.domain.admin.supply.shelly.SupplyShelly;
import org.lucoenergia.conluz.infrastructure.admin.user.UserResponse;

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
    private final SupplyContract contract;
    private final SupplyDistributor distributor;
    private final SupplyDatadis datadis;
    private final SupplyShelly shelly;

    public SupplyResponse(Supply supply) {
        this.id = supply.getId();
        this.code = supply.getCode();
        this.name = supply.getName();
        this.address = supply.getAddress();
        this.addressRef = supply.getAddressRef();
        this.partitionCoefficient = supply.getPartitionCoefficient();
        this.enabled = supply.getEnabled();
        this.user = supply.getUser() != null ? new UserResponse(supply.getUser()) : null;
        this.contract = supply.getContract();
        this.distributor = supply.getDistributor();
        this.datadis = supply.getDatadis();
        this.shelly = supply.getShelly();
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

    public SupplyContract getContract() {
        return contract;
    }

    public SupplyDistributor getDistributor() {
        return distributor;
    }

    public SupplyDatadis getDatadis() {
        return datadis;
    }

    public SupplyShelly getShelly() {
        return shelly;
    }
}
