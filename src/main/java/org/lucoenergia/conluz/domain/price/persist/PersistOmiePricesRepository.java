package org.lucoenergia.conluz.domain.price.persist;

import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.price.PriceByHour;

import java.util.List;

public interface PersistOmiePricesRepository {

    void persistPrices(@NotNull List<PriceByHour> prices);
}
