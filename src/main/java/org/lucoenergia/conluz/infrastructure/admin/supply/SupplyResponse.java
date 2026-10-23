package org.lucoenergia.conluz.infrastructure.admin.supply;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.SupplyCapabilitiesResponse;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.MemberRecordResponse;
import org.lucoenergia.conluz.infrastructure.admin.supply.contract.SupplyContractResponse;
import org.lucoenergia.conluz.infrastructure.admin.supply.distributor.SupplyDistributorResponse;
import org.lucoenergia.conluz.infrastructure.admin.supply.shelly.SupplyShellyResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.CommunityReferenceResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.UserReferenceResponse;

import java.util.UUID;

@Schema(requiredProperties = {"id", "code", "user", "ownerRecord", "name", "address", "addressRef", "enabled",
        "contract", "distributor", "shelly", "capabilities", "community"})
public class SupplyResponse {

    @Schema(description = "Internal unique identifier of the supply", example = "ebbe60d1-f9db-455c-8c2d-c34ae7a1c23c")
    private final UUID id;
    @Schema(description = "Code that identifies the supply", example = "ES0031300119158001DL0Y")
    private final String code;
    @Schema(description = "Owner of the supply, by id only. Null when the supply has no owner.",
            types = {"object", "null"})
    private final UserReferenceResponse user;
    @Schema(description = "The owner's member record in the supply's community. Null when the supply has no owner, "
            + "or its owner has no membership in that community.", types = {"object", "null"})
    private final MemberRecordResponse ownerRecord;
    @Schema(description = "Name of the supply", example = "My house", types = {"string", "null"})
    private final String name;
    @Schema(description = "Address of the supply", example = "Fake Street 123")
    private final String address;
    @Schema(description = "Reference ID of the address", example = "4ASDF654ASDF89ASD", types = {"string", "null"})
    private final String addressRef;
    @Schema(description = "Whether the supply is enabled or disabled", example = "true")
    private final Boolean enabled;
    @Schema(description = "Contract information of the supply", types = {"object", "null"})
    private final SupplyContractResponse contract;
    @Schema(description = "Distributor information of the supply", types = {"object", "null"})
    private final SupplyDistributorResponse distributor;
    @Schema(description = "Shelly device information of the supply", types = {"object", "null"})
    private final SupplyShellyResponse shelly;
    @Schema(description = "Community the supply belongs to")
    private final CommunityReferenceResponse community;

    @Schema(description = "What the caller may do with this supply.")
    private final SupplyCapabilitiesResponse capabilities;

    /**
     * @param ownerRecord the owner's membership in the supply's community (MEM-026), or {@code null} when the supply
     *                    has no owner or its owner has none; a listing reads those records once for the whole page.
     */
    public SupplyResponse(Supply supply, SupplyCapabilitiesResponse capabilities, CommunityMembership ownerRecord) {
        this.id = supply.getId();
        this.code = supply.getCode();
        this.name = supply.getName();
        this.address = supply.getAddress();
        this.addressRef = supply.getAddressRef();
        this.enabled = supply.getEnabled();
        this.user = supply.getUser() != null ? new UserReferenceResponse(supply.getUser().getId()) : null;
        this.ownerRecord = MemberRecordResponse.ofRecord(ownerRecord);
        this.contract = supply.getContract() != null ? new SupplyContractResponse(supply.getContract()) : null;
        this.distributor = supply.getDistributor() != null ? new SupplyDistributorResponse(supply.getDistributor()) : null;
        this.shelly = supply.getShelly() != null ? new SupplyShellyResponse(supply.getShelly()) : null;
        this.capabilities = capabilities;
        // Dereferenced unguarded: supplies.community_id is NOT NULL with a foreign key, so a null
        // here is a bug that must surface rather than serialise into a non-nullable field.
        this.community = new CommunityReferenceResponse(supply.getCommunity().getId(),
                supply.getCommunity().getName());
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public UserReferenceResponse getUser() {
        return user;
    }

    public MemberRecordResponse getOwnerRecord() {
        return ownerRecord;
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

    public Boolean getEnabled() {
        return enabled;
    }

    public SupplyContractResponse getContract() {
        return contract;
    }

    public SupplyDistributorResponse getDistributor() {
        return distributor;
    }

    public SupplyShellyResponse getShelly() {
        return shelly;
    }

    public SupplyCapabilitiesResponse getCapabilities() {
        return capabilities;
    }

    public CommunityReferenceResponse getCommunity() {
        return community;
    }
}
