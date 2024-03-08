package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.UUID;

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
    @Positive
    private Float partitionCoefficient;

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

    public Supply mapToSupply() {
        Supply.Builder builder = new Supply.Builder();
        builder.withCode(code)
                .withAddress(address)
                .withPartitionCoefficient(partitionCoefficient)
                .withUser(new User.Builder().personalId(personalId).build());
        return builder.build();
    }
}
