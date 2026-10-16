package org.lucoenergia.conluz.infrastructure.admin.user;

/**
 * The OpenAPI wording shared by the member data fields of {@link UserResponse} (MEM-026).
 */
final class MemberDataSchema {

    static final String SOURCE = " Inside a response scoped to a community, read from the user's member record in "
            + "that community. When a community admin reads or updates another user, read from that user's member "
            + "record in the community they administer with the lowest id. Otherwise read from the account.";

    private MemberDataSchema() {
    }
}
