package org.lucoenergia.conluz.domain.admin.community;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.infrastructure.shared.uuid.ValidUUID;

import java.util.Objects;
import java.util.UUID;

public class Community {

    @NotNull
    @ValidUUID
    private UUID id;
    @NotBlank
    private String name;
    @NotBlank
    private String code;
    private String legalId;
    private String address;
    @NotNull
    private Boolean enabled;

    private Community(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.code = builder.code;
        this.legalId = builder.legalId;
        this.address = builder.address;
        this.enabled = builder.enabled != null ? builder.enabled : true;
    }

    public void initializeUuid() {
        id = UUID.randomUUID();
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

    public Boolean isEnabled() {
        return enabled;
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

        public Community build() {
            return new Community(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Community community)) return false;
        return Objects.equals(getId(), community.getId()) && Objects.equals(getCode(), community.getCode());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getCode());
    }

    @Override
    public String toString() {
        return "Community{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", code='" + code + '\'' +
                '}';
    }
}
