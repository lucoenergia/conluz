package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;

@Schema(requiredProperties = {
        "role"
})
public class UpdateMembershipRoleBody {

    @NotNull
    private CommunityRole role;

    public CommunityRole getRole() {
        return role;
    }

    public void setRole(CommunityRole role) {
        this.role = role;
    }
}
