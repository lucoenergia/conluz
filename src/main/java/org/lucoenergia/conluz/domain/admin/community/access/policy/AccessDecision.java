package org.lucoenergia.conluz.domain.admin.community.access.policy;

/**
 * The outcome of an access rule, expressed without reference to HTTP or to exceptions.
 *
 * <p>Denials are separated by <em>visibility</em>, which is what lets a single rule serve two
 * callers: a guard adapter turns {@link #NOT_VISIBLE} into the aggregate's {@code *NotFoundException}
 * (→ 404) and {@link #FORBIDDEN} into {@code false} (→ 403), while a capability assembler reads the
 * same value over already-loaded entities and simply reports whether the action is offered.</p>
 */
public enum AccessDecision {

    /**
     * The caller cannot see that the target exists. Answering "forbidden" here would itself leak
     * the target's existence.
     */
    NOT_VISIBLE,

    /**
     * The caller can see the target but may not perform this action on it.
     */
    FORBIDDEN,

    /**
     * The caller may perform this action.
     */
    ALLOWED;

    public boolean isAllowed() {
        return this == ALLOWED;
    }
}
