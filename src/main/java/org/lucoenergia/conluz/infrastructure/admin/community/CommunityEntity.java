package org.lucoenergia.conluz.infrastructure.admin.community;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.util.Objects;
import java.util.UUID;

@Entity(name = "communities")
public class CommunityEntity {

    @Id
    private UUID id;
    private String name;
    @Column(unique = true, nullable = false)
    private String code;
    @Column(name = "legal_id", unique = true)
    private String legalId;
    private String address;
    private Boolean enabled;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLegalId() {
        return legalId;
    }

    public void setLegalId(String legalId) {
        this.legalId = legalId;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
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
        if (!(o instanceof CommunityEntity that)) return false;
        return Objects.equals(getId(), that.getId()) && Objects.equals(getCode(), that.getCode());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getCode());
    }

    public static class Builder {

        private UUID id;
        private String name;
        private String code;
        private String legalId;
        private String address;
        private Boolean enabled;

        public Builder withId(UUID id) {
            this.id = id;
            return this;
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withCode(String code) {
            this.code = code;
            return this;
        }

        public Builder withLegalId(String legalId) {
            this.legalId = legalId;
            return this;
        }

        public Builder withAddress(String address) {
            this.address = address;
            return this;
        }

        public Builder withEnabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public CommunityEntity build() {
            CommunityEntity entity = new CommunityEntity();
            entity.setId(id);
            entity.setName(name);
            entity.setCode(code);
            entity.setLegalId(legalId);
            entity.setAddress(address);
            entity.setEnabled(enabled);
            return entity;
        }
    }
}
