package org.lucoenergia.conluz.domain.shared;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public class SupplyCode {

    /**
     * The length of a complete CUPS.
     */
    public static final int CUPS_LENGTH = 22;
    /**
     * The length of a CUPS written without its two-character border point suffix.
     */
    public static final int SHORT_CUPS_LENGTH = 20;
    /**
     * The suffix a {@link #SHORT_CUPS_LENGTH}-character CUPS is completed with.
     */
    public static final String SHORT_CUPS_SUFFIX = "0F";

    private final String code;

    public SupplyCode(String code) {
        this.code = code;
    }

    public static SupplyCode of(String code) {
        return new SupplyCode(code);
    }

    /**
     * The {@link #CUPS_LENGTH}-character CUPS a code stands for, or empty when the code cannot be one. A
     * {@link #SHORT_CUPS_LENGTH}-character CUPS is completed with {@link #SHORT_CUPS_SUFFIX}; a CUPS that is already
     * the full length is returned unchanged, which makes this idempotent. The case is kept.
     *
     * <p>Whitespace is rejected rather than trimmed: a CUPS holds no blank spaces, and silently stripping them would
     * change the identifier.</p>
     */
    public static Optional<String> toFullLength(String cups) {
        if (cups == null || cups.isEmpty() || containsWhitespace(cups)) {
            return Optional.empty();
        }
        if (cups.length() == SHORT_CUPS_LENGTH) {
            return Optional.of(cups + SHORT_CUPS_SUFFIX);
        }
        if (cups.length() == CUPS_LENGTH) {
            return Optional.of(cups);
        }
        return Optional.empty();
    }

    /**
     * A code written to a supply: upper-cased, then accepted only if it is a CUPS by {@link #toFullLength}. Codes
     * stored before this rule are not revalidated (#532).
     *
     * @return the upper-cased code, or empty if it is not a CUPS
     */
    public static Optional<SupplyCode> ofCups(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String code = raw.toUpperCase(Locale.ROOT);
        return toFullLength(code).map(full -> SupplyCode.of(code));
    }

    /**
     * The stored codes, upper-cased, that are the same CUPS as this one: its {@link #CUPS_LENGTH}-character form, and
     * the {@link #SHORT_CUPS_LENGTH}-character form when that one ends in {@link #SHORT_CUPS_SUFFIX}.
     *
     * @throws IllegalStateException if this code is not a CUPS; only a code from {@link #ofCups} is compared
     */
    public List<String> sameCupsForms() {
        String full = toFullLength(code.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalStateException("Not a CUPS"));
        if (full.endsWith(SHORT_CUPS_SUFFIX)) {
            return List.of(full, full.substring(0, SHORT_CUPS_LENGTH));
        }
        return List.of(full);
    }

    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public String getCode() {
        return code;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SupplyCode that = (SupplyCode) o;
        return Objects.equals(code, that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code);
    }
}
