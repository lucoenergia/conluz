package org.lucoenergia.conluz.domain.admin.community;

import java.util.List;
import java.util.UUID;

public class CommunityWithStats {

    private final Community community;
    private final List<String> adminNames;
    private final int memberCount;
    private final int supplyPointCount;

    public CommunityWithStats(Community community, List<String> adminNames, int memberCount, int supplyPointCount) {
        this.community = community;
        this.adminNames = adminNames;
        this.memberCount = memberCount;
        this.supplyPointCount = supplyPointCount;
    }

    public UUID getId() {
        return community.getId();
    }

    public String getName() {
        return community.getName();
    }

    public String getCode() {
        return community.getCode();
    }

    public String getLegalId() {
        return community.getLegalId();
    }

    public String getAddress() {
        return community.getAddress();
    }

    public Boolean isEnabled() {
        return community.isEnabled();
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
}
