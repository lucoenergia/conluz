package org.lucoenergia.conluz.domain.price.persist;

import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.price.PriceByHour;

import java.util.List;

public interface PersistOmiePricesRepository {

    /**
     * Stores the prices, each replacing whatever was stored at the same instant.
     *
     * @throws RuntimeException when the prices cannot be written, so that the caller does not report as stored
     *         prices that were not
     */
    void persistPrices(@NotNull List<PriceByHour> prices);
}
