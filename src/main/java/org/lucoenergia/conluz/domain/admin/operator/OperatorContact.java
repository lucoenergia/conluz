package org.lucoenergia.conluz.domain.admin.operator;

/**
 * How to reach the organisation operating this instance: the last-resort contact for someone who cannot sign in and
 * so has no community to turn to (CTC-004). Name and email are always present; phone, schedule and notes are
 * {@code null} when not configured.
 */
public record OperatorContact(String name, String email, String phone, String schedule, String notes) {
}
