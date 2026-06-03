package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;

import java.util.UUID;

@Schema(requiredProperties = {
        "userId", "role"
})
public class CreateMembershipBody {

    @NotNull
    private UUID userId;

    @NotNull
    private CommunityRole role;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public CommunityRole getRole() {
        return role;
    }

    public void setRole(CommunityRole role) {
        this.role = role;
    }
}
