package org.lucoenergia.conluz.infrastructure.admin.supply.tariff;

import org.lucoenergia.conluz.domain.admin.supply.tariff.TaxRatePeriods;

/**
 * The validated rate periods of the two taxes an estimated kWh is priced with.
 *
 * @param vat            the VAT periods
 * @param electricityTax the electricity tax (Impuesto Especial sobre la Electricidad) periods
 */
public record EstimatedTaxRates(TaxRatePeriods vat, TaxRatePeriods electricityTax) {
}
