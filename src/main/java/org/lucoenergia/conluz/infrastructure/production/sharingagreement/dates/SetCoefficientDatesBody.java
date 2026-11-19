package org.lucoenergia.conluz.infrastructure.production.sharingagreement.dates;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDate;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesRequest;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.LeavingClosureDate;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * The body of both the write and the preview of an agreement's coefficient dates.
 */
@Schema(requiredProperties = {"coefficients", "leavingClosures"})
public class SetCoefficientDatesBody {

    @NotNull
    @Schema(description = "The coefficients of this agreement to activate or correct, each with its own date. May " +
            "be empty when leavingClosures is not. Each coefficient at most once.")
    private List<@Valid @NotNull CoefficientDateBody> coefficients;

    @NotNull
    @Schema(description = "The supplies of the plant absent from this agreement to close, each with its own date. " +
            "May be empty when coefficients is not. Each supply at most once.")
    private List<@Valid @NotNull LeavingClosureBody> leavingClosures;

    public List<CoefficientDateBody> getCoefficients() {
        return coefficients;
    }

    public void setCoefficients(List<CoefficientDateBody> coefficients) {
        this.coefficients = coefficients;
    }

    public List<LeavingClosureBody> getLeavingClosures() {
        return leavingClosures;
    }

    public void setLeavingClosures(List<LeavingClosureBody> leavingClosures) {
        this.leavingClosures = leavingClosures;
    }

    @JsonIgnore
    @AssertTrue(message = "coefficients and leavingClosures must not both be empty")
    public boolean isAnyDateGiven() {
        return coefficients == null || leavingClosures == null
                || !coefficients.isEmpty() || !leavingClosures.isEmpty();
    }

    @JsonIgnore
    @AssertTrue(message = "each coefficient must be listed at most once")
    public boolean isEachCoefficientListedOnce() {
        return listedOnce(coefficients, CoefficientDateBody::getCoefficientId);
    }

    @JsonIgnore
    @AssertTrue(message = "each supply must be listed at most once")
    public boolean isEachSupplyListedOnce() {
        return listedOnce(leavingClosures, LeavingClosureBody::getSupplyId);
    }

    private static <T> boolean listedOnce(List<T> items, Function<T, Object> id) {
        if (items == null) {
            return true;
        }
        List<Object> ids = items.stream().filter(Objects::nonNull).map(id).filter(Objects::nonNull).toList();
        return ids.stream().distinct().count() == ids.size();
    }

    public CoefficientDatesRequest toRequest() {
        return new CoefficientDatesRequest(
                coefficients.stream().map(item -> new CoefficientDate(item.getCoefficientId(), item.getValidFrom()))
                        .toList(),
                leavingClosures.stream().map(item -> new LeavingClosureDate(item.getSupplyId(), item.getClosedOn()))
                        .toList());
    }
}
