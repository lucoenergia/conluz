package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the caller may do with this sharing agreement.
 *
 * <p>Every field answers "may this caller do this?", and nothing else. A capability is never a
 * statement about the resource's <em>state</em>: an agreement that is already published still
 * reports {@code canManage = true} to an admin, because the admin is allowed to manage it and it is
 * the agreement's {@code status} that says the action would be rejected right now. Clients read
 * both.</p>
 */
@Schema(description = "What the caller may do with this sharing agreement.", requiredProperties = {"canRead", "canManage"})
public class SharingAgreementCapabilitiesResponse {

    @Schema(description = "Whether the caller may read this agreement, its partition coefficients and its file.")
    private final boolean canRead;

    @Schema(description = "Whether the caller may update, delete, publish or revert this agreement and edit its coefficients. Whether a particular one of those actions is possible right now also depends on the agreement's status, which this does not reflect.")
    private final boolean canManage;

    private SharingAgreementCapabilitiesResponse(Builder builder) {
        this.canRead = builder.canRead;
        this.canManage = builder.canManage;
    }

    public boolean isCanRead() {
        return canRead;
    }

    public boolean isCanManage() {
        return canManage;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Named setters rather than a constructor: every field is a boolean, so a positional
     * constructor would let two capabilities be swapped without the compiler noticing.
     */
    public static final class Builder {

        private boolean canRead;
        private boolean canManage;

        public Builder withCanRead(boolean canRead) {
            this.canRead = canRead;
            return this;
        }

        public Builder withCanManage(boolean canManage) {
            this.canManage = canManage;
            return this;
        }

        public SharingAgreementCapabilitiesResponse build() {
            return new SharingAgreementCapabilitiesResponse(this);
        }
    }
}
