package org.lucoenergia.conluz.domain.admin.community;

import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.shared.uuid.ValidUUID;

import java.util.Objects;
import java.util.UUID;

public class CommunityMembership {

    @NotNull
    @ValidUUID
    private final UUID id;
    @NotNull
    private final User user;
    @NotNull
    private final Community community;
    @NotNull
    private final CommunityRole role;
    @NotNull
    private final Boolean enabled;

    private CommunityMembership(Builder builder) {
        this.id = builder.id;
        this.user = builder.user;
        this.community = builder.community;
        this.role = builder.role;
        this.enabled = builder.enabled != null ? builder.enabled : true;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Community getCommunity() {
        return community;
    }

    public CommunityRole getRole() {
        return role;
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public static class Builder {
        private UUID id;
        private User user;
        private Community community;
        private CommunityRole role;
        private Boolean enabled;

        public Builder withId(UUID id) {
            this.id = id;
            return this;
        }

        public Builder withUser(User user) {
            this.user = user;
            return this;
        }

        public Builder withCommunity(Community community) {
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

        public CommunityMembership build() {
            return new CommunityMembership(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommunityMembership that)) return false;
        return Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
