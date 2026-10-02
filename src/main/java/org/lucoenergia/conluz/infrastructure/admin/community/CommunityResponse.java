package org.lucoenergia.conluz.infrastructure.admin.community;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityWithStats;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.CommunityCapabilitiesResponse;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Schema(requiredProperties = {"id", "name", "code", "legalId", "address", "enabled", "adminNames",
        "memberCount", "supplyPointCount", "capabilities"})
public class CommunityResponse {

    private final UUID id;
    private final String name;
    private final String code;
    @Schema(types = {"string", "null"})
    private final String legalId;
    @Schema(types = {"string", "null"})
    private final String address;
    private final Boolean enabled;

    @Schema(description = "Display names of community administrators")
    private final List<String> adminNames;

    @Schema(description = "Total number of members in the community")
    private final int memberCount;

    @Schema(description = "Total number of supply points in the community")
    private final int supplyPointCount;

    @Schema(description = "What the caller may do with this community.")
    private final CommunityCapabilitiesResponse capabilities;

    public CommunityResponse(Community community, CommunityCapabilitiesResponse capabilities) {
        this(community, Collections.emptyList(), 0, 0, capabilities);
    }

    public CommunityResponse(Community community, List<String> adminNames, int memberCount, int supplyPointCount,
                             CommunityCapabilitiesResponse capabilities) {
        this.id = community.getId();
        this.name = community.getName();
        this.code = community.getCode();
        this.legalId = community.getLegalId();
        this.address = community.getAddress();
        this.enabled = community.isEnabled();
        this.adminNames = adminNames;
        this.memberCount = memberCount;
        this.supplyPointCount = supplyPointCount;
        this.capabilities = capabilities;
    }

    public CommunityResponse(CommunityWithStats community, CommunityCapabilitiesResponse capabilities) {
        this.id = community.getId();
        this.name = community.getName();
        this.code = community.getCode();
        this.legalId = community.getLegalId();
        this.address = community.getAddress();
        this.enabled = community.isEnabled();
        this.adminNames = community.getAdminNames();
        this.memberCount = community.getMemberCount();
        this.supplyPointCount = community.getSupplyPointCount();
        this.capabilities = capabilities;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public String getLegalId() {
        return legalId;
    }

    public String getAddress() {
        return address;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public List<String> getAdminNames() {
        return adminNames;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public int getSupplyPointCount() {
        return supplyPointCount;
    }

    public CommunityCapabilitiesResponse getCapabilities() {
        return capabilities;
    }
}
