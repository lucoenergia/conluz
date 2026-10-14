package org.lucoenergia.conluz.domain.admin.community;

import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.uuid.ValidUUID;

import java.math.BigDecimal;
import java.time.Instant;
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
    /**
     * The community's record of the member: the data this community entered about the person,
     * which can differ from what another community entered about the same person. The member
     * number, contact email, address and phone are null when the community has not recorded them.
     */
    private final String fullName;
    private final Integer memberNumber;
    private final String contactEmail;
    private final String address;
    private final String phoneNumber;
    /**
     * When the membership was accepted, or null while it is pending.
     */
    private final Instant acceptedAt;
    /**
     * The user who invited this membership, or null when no inviting user is recorded.
     */
    private final UUID invitedBy;
    /**
     * When the membership was invited, or null when it was not created by an invitation.
     */
    private final Instant invitedAt;

    private CommunityMembership(Builder builder) {
        this.id = builder.id;
        this.user = builder.user;
        this.community = builder.community;
        this.role = builder.role;
        this.enabled = builder.enabled != null ? builder.enabled : true;
        this.investmentEur = builder.investmentEur;
        this.fullName = builder.fullName;
        this.memberNumber = builder.memberNumber;
        this.contactEmail = builder.contactEmail;
        this.address = builder.address;
        this.phoneNumber = builder.phoneNumber;
        this.acceptedAt = builder.acceptedAt;
        this.invitedBy = builder.invitedBy;
        this.invitedAt = builder.invitedAt;
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

    public String getFullName() {
        return fullName;
    }

    public Integer getMemberNumber() {
        return memberNumber;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getAddress() {
        return address;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    /**
     * Whether the membership is active: accepted and enabled. Pending and disabled are independent
     * states, and a membership in either of them is not active (ADR-0003 §2).
     *
     * <p>This is the single definition of "active". Code that needs it calls this method instead
     * of reading {@link #getAcceptedAt()} or {@link #isEnabled()}; queries that filter on it in the
     * database use {@code ActiveMembershipJpql}, which spells the same condition.</p>
     */
    public boolean isActive() {
        return acceptedAt != null && Boolean.TRUE.equals(enabled);
    }

    /**
     * Whether the membership is pending: it has not been accepted. Independent of whether it is enabled
     * (ADR-0003 §2).
     */
    public boolean isPending() {
        return acceptedAt == null;
    }

    /**
     * Whether the membership is awaiting acceptance: pending and enabled, so the community still wants the member to
     * accept it. A disabled membership grants nothing even once accepted.
     *
     * <p>This is the single definition of "awaiting acceptance". Queries that filter on it in the database use
     * {@code ActiveMembershipJpql.AWAITING_ACCEPTANCE}, which spells the same condition.</p>
     */
    public boolean isAwaitingAcceptance() {
        return acceptedAt == null && Boolean.TRUE.equals(enabled);
    }

    public UUID getInvitedBy() {
        return invitedBy;
    }

    public Instant getInvitedAt() {
        return invitedAt;
    }

    public static class Builder {
        private UUID id;
        private User user;
        private Community community;
        private CommunityRole role;
        private Boolean enabled;
        private BigDecimal investmentEur;
        private String fullName;
        private Integer memberNumber;
        private String contactEmail;
        private String address;
        private String phoneNumber;
        private Instant acceptedAt;
        private UUID invitedBy;
        private Instant invitedAt;

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

        public Builder withFullName(String fullName) {
            this.fullName = fullName;
            return this;
        }

        public Builder withMemberNumber(Integer memberNumber) {
            this.memberNumber = memberNumber;
            return this;
        }

        public Builder withContactEmail(String contactEmail) {
            this.contactEmail = contactEmail;
            return this;
        }

        public Builder withAddress(String address) {
            this.address = address;
            return this;
        }

        public Builder withPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
            return this;
        }

        public Builder withAcceptedAt(Instant acceptedAt) {
            this.acceptedAt = acceptedAt;
            return this;
        }

        public Builder withInvitedBy(UUID invitedBy) {
            this.invitedBy = invitedBy;
            return this;
        }

        public Builder withInvitedAt(Instant invitedAt) {
            this.invitedAt = invitedAt;
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
