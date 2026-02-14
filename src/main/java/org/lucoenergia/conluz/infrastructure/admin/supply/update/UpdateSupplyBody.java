package org.lucoenergia.conluz.infrastructure.admin.supply.update;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSupplyDto;
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
    @NotEmpty
    private String addressRef;
    @Positive
    private Float partitionCoefficient;


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

    public UpdateSupplyDto mapToSupply() {
        UpdateSupplyDto.Builder builder = new UpdateSupplyDto.Builder();
        builder.code(code)
                .name(name)
                .address(address)
                .addressRef(addressRef)
                .partitionCoefficient(partitionCoefficient);
        return builder.build();
    }
}
