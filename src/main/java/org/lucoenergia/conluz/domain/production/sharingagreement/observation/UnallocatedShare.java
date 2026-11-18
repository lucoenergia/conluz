package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

/**
 * The share of the plant's production no supply with data was observed to receive while an agreement was
 * applied, next to the share the agreement registers for the supplies without data.
 *
 * @param unallocated               1 − ΣE / ΣG over the days used, weighted by production
 * @param registeredShareWithoutData the registered share of the supplies without data, weighted by production
 * @param days                      the number of plant-valid days used
 */
public record UnallocatedShare(double unallocated, double registeredShareWithoutData, int days) {
}
