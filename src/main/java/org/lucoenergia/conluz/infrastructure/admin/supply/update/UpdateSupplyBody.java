package org.lucoenergia.conluz.infrastructure.admin.supply.update;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSupplyDto;


@Schema(requiredProperties = {
        "code", "address"
})
public class UpdateSupplyBody {

    @NotEmpty
    private String code;
    private String name;
    @NotEmpty
    private String address;
    @NotEmpty
    private String addressRef;


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

    public UpdateSupplyDto mapToSupply() {
        UpdateSupplyDto.Builder builder = new UpdateSupplyDto.Builder();
        builder.code(code)
                .name(name)
                .address(address)
                .addressRef(addressRef);
        return builder.build();
    }
}
