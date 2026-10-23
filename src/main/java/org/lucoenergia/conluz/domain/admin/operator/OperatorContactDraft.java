package org.lucoenergia.conluz.domain.admin.operator;

/**
 * The operator contact as the instance configures it, before {@link OperatorContactValidator#validate} trims and
 * checks it. Any value may be {@code null} or blank when it is not configured.
 */
public record OperatorContactDraft(String name, String email, String phone, String schedule, String notes) {
}
