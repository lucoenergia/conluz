package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.User;

@Schema(requiredProperties = {
        "code", "personalId", "address", "partitionCoefficient"
})
public class CreateSupplyBody {

    @NotEmpty
    private String code;
    @NotEmpty
    private String personalId;
    @NotEmpty
    private String address;
    @NotEmpty
    private String addressRef;
    @Positive
    private Float partitionCoefficient;
    private String name;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getAddressRef() {
        return addressRef;
    }

    public void setAddressRef(String addressRef) {
        this.addressRef = addressRef;
    }

    public Float getPartitionCoefficient() {
        return partitionCoefficient;
    }

    public void setPartitionCoefficient(Float partitionCoefficient) {
        this.partitionCoefficient = partitionCoefficient;
    }

    public String getPersonalId() {
        return personalId;
    }

    public void setPersonalId(String personalId) {
        this.personalId = personalId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Supply mapToSupply() {
        Supply.Builder builder = new Supply.Builder();
        builder.withCode(code.trim())
                .withAddress(address.trim())
                .withAddressRef(addressRef.trim())
                .withPartitionCoefficient(partitionCoefficient)
                .withUser(new User.Builder().personalId(personalId.trim()).build());

        if (name != null && !name.isBlank()) {
            builder.withName(name.trim());
        }
        if (addressRef != null && !addressRef.isBlank()) {
            builder.withAddressRef(addressRef.trim());
        }

        return builder.build();
    }
}
