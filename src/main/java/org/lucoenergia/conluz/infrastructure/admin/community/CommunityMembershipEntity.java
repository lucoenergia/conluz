package org.lucoenergia.conluz.infrastructure.admin.community;

import jakarta.persistence.*;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;

import java.util.Objects;
import java.util.UUID;

@Entity(name = "community_memberships")
public class CommunityMembershipEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id")
    private CommunityEntity community;

    @Enumerated(EnumType.STRING)
    private CommunityRole role;

    private Boolean enabled;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public CommunityEntity getCommunity() {
        return community;
    }

    public void setCommunity(CommunityEntity community) {
        this.community = community;
    }

    public CommunityRole getRole() {
        return role;
    }

    public void setRole(CommunityRole role) {
        this.role = role;
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommunityMembershipEntity that)) return false;
        return Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    public static class Builder {

        private UUID id;
        private UserEntity user;
        private CommunityEntity community;
        private CommunityRole role;
        private Boolean enabled;

        public Builder withId(UUID id) {
            this.id = id;
            return this;
        }

        public Builder withUser(UserEntity user) {
            this.user = user;
            return this;
        }

        public Builder withCommunity(CommunityEntity community) {
            this.community = community;
            return this;
        }

        public Builder withRole(CommunityRole role) {
            this.role = role;
            return this;
        }

        public Builder withEnabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public CommunityMembershipEntity build() {
            CommunityMembershipEntity entity = new CommunityMembershipEntity();
            entity.setId(id);
            entity.setUser(user);
            entity.setCommunity(community);
            entity.setRole(role);
            entity.setEnabled(enabled);
            return entity;
        }
    }
}
