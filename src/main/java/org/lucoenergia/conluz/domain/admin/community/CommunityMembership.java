package org.lucoenergia.conluz.domain.admin.community;

import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.shared.uuid.ValidUUID;

import java.math.BigDecimal;
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
    /**
     * The member's initial contribution in euros, or null when none has been recorded. Personal
     * financial data: it reaches the outside only through the payback endpoint, under that
     * endpoint's guard, and is deliberately absent from every membership and user response.
     */
    private final BigDecimal investmentEur;

    private CommunityMembership(Builder builder) {
        this.id = builder.id;
        this.user = builder.user;
        this.community = builder.community;
        this.role = builder.role;
        this.enabled = builder.enabled != null ? builder.enabled : true;
        this.investmentEur = builder.investmentEur;
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

    public BigDecimal getInvestmentEur() {
        return investmentEur;
    }

    public static class Builder {
        private UUID id;
        private User user;
        private Community community;
        private CommunityRole role;
        private Boolean enabled;
        private BigDecimal investmentEur;

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

        public Builder withInvestmentEur(BigDecimal investmentEur) {
            this.investmentEur = investmentEur;
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
