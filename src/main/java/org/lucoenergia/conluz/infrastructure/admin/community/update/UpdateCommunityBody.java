package org.lucoenergia.conluz.infrastructure.admin.community.update;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.lucoenergia.conluz.domain.admin.community.Community;

@Schema(requiredProperties = {
        "name", "code"
})
public class UpdateCommunityBody {

    @NotBlank
    private String name;

    @NotBlank
    private String code;

    private String legalId;

    private String address;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLegalId() {
        return legalId;
    }

    public void setLegalId(String legalId) {
        this.legalId = legalId;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Community mapToCommunity() {
        return new Community.Builder()
                .withName(name)
                .withCode(code)
                .withLegalId(legalId)
                .withAddress(address)
                .build();
    }
}
