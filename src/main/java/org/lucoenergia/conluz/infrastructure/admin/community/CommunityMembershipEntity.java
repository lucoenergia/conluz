package org.lucoenergia.conluz.infrastructure.admin.community;

import jakarta.persistence.*;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;

import java.math.BigDecimal;
import java.time.Instant;
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

    /**
     * The member's initial contribution in euros, or null when none has been recorded. Null is
     * not zero: no amount is stored for a membership that has never had one set.
     */
    @Column(name = "investment_eur", precision = 12, scale = 2)
    private BigDecimal investmentEur;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "member_number")
    private Integer memberNumber;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "address")
    private String address;

    @Column(name = "phone_number")
    private String phoneNumber;

    /**
     * When the membership was accepted, or null while it is pending.
     */
    @Column(name = "accepted_at")
    private Instant acceptedAt;

    /**
     * The user who invited this membership, or null when no inviting user is recorded. A plain id
     * rather than an association: the database sets it to null when that user is deleted.
     */
    @Column(name = "invited_by")
    private UUID invitedBy;

    /**
     * When the membership was invited, or null when it was not created by an invitation.
     */
    @Column(name = "invited_at")
    private Instant invitedAt;

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

    public BigDecimal getInvestmentEur() {
        return investmentEur;
    }

    public void setInvestmentEur(BigDecimal investmentEur) {
        this.investmentEur = investmentEur;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Integer getMemberNumber() {
        return memberNumber;
    }

    public void setMemberNumber(Integer memberNumber) {
        this.memberNumber = memberNumber;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public UUID getInvitedBy() {
        return invitedBy;
    }

    public void setInvitedBy(UUID invitedBy) {
        this.invitedBy = invitedBy;
    }

    public Instant getInvitedAt() {
        return invitedAt;
    }

    public void setInvitedAt(Instant invitedAt) {
        this.invitedAt = invitedAt;
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

        public CommunityMembershipEntity build() {
            CommunityMembershipEntity entity = new CommunityMembershipEntity();
            entity.setId(id);
            entity.setUser(user);
            entity.setCommunity(community);
            entity.setRole(role);
            entity.setEnabled(enabled);
            entity.setInvestmentEur(investmentEur);
            entity.setFullName(fullName);
            entity.setMemberNumber(memberNumber);
            entity.setContactEmail(contactEmail);
            entity.setAddress(address);
            entity.setPhoneNumber(phoneNumber);
            entity.setAcceptedAt(acceptedAt);
            entity.setInvitedBy(invitedBy);
            entity.setInvitedAt(invitedAt);
            return entity;
        }
    }
}
