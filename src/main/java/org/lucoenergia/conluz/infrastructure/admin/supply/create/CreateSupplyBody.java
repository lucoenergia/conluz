package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.UUID;

@Schema(requiredProperties = {
        "code", "personalId", "address", "communityId"
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
    private String name;
    @NotNull
    private UUID communityId;

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

    public UUID getCommunityId() {
        return communityId;
    }

    public void setCommunityId(UUID communityId) {
        this.communityId = communityId;
    }

    public Supply mapToSupply() {
        Supply.Builder builder = new Supply.Builder();
        builder.withCode(code != null ? code.trim() : null)
                .withAddress(address != null ? address.trim() : null)
                .withAddressRef(addressRef != null ? addressRef.trim() : null)
                .withUser(personalId != null ? new User.Builder().personalId(personalId.trim()).build() : null);

        if (name != null && !name.isBlank()) {
            builder.withName(name.trim());
        }
        if (addressRef != null && !addressRef.isBlank()) {
            builder.withAddressRef(addressRef.trim());
        }

        return builder.build();
    }
}
