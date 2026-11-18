package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import java.util.UUID;

/**
 * A supply as the observation reports it.
 */
public record ObservedSupply(UUID id, String code, String name) {
}
