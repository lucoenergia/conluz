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
    @Schema(description = "The DNI/NIE/NIF of the existing user who owns the supply. It is normalised before the owner is looked up: whitespace (including the no-break space), dots and hyphens are removed and letters are upper-cased, so 12.345.678-a and 12345678A are the same identifier.")
    @NotEmpty
    private String personalId;
    @NotEmpty
    private String address;
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
