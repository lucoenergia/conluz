package org.lucoenergia.conluz.infrastructure.shared.web.error;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Stable, machine-readable error codes for {@link RestErrorDetail#getCode()}.
 *
 * <p>A newer backend may emit a code that an already-deployed conluz-web does not know. The
 * generated TypeScript union is a compile-time construct and offers no runtime protection.
 * Clients must always have a default branch and fall back to rendering {@code message}.
 * Adding a value here is additive and non-breaking; renaming or removing one is a breaking
 * API change.
 *
 * <p>Naming: SCREAMING_SNAKE_CASE, namespaced by domain area (e.g. {@code USER_}, {@code
 * SUPPLY_}). Values are paired with {@link RestErrorDetail#getParams()} keys in camelCase,
 * used as i18n interpolation variables by clients.
 */
@Schema(description = "Stable machine-readable error code.")
public enum RestErrorCode {

    USER_LAST_PLATFORM_ADMIN,
    SHARING_AGREEMENT_NOT_DRAFT,
    SHARING_AGREEMENT_HAS_NO_COEFFICIENTS,
    SHARING_AGREEMENT_DUPLICATE_CUPS,
    DISTRIBUTOR_FILE_FILENAME_SHAPE_INVALID,
    DISTRIBUTOR_FILE_PLANT_REGULATORY_CODE_MISSING,
    DISTRIBUTOR_FILE_FILENAME_REGULATORY_CODE_MISMATCH,
    DISTRIBUTOR_FILE_VALUE_DECIMAL_SEPARATOR_INVALID,
    DISTRIBUTOR_FILE_VALUE_SCALE_INVALID,
    DISTRIBUTOR_FILE_CUPS_LENGTH_INVALID,
    DISTRIBUTOR_FILE_CUPS_DUPLICATE,
    DISTRIBUTOR_FILE_COEFFICIENT_SUM_INVALID,
    DISTRIBUTOR_FILE_CUPS_UNKNOWN,
    DISTRIBUTOR_FILE_LINE_MALFORMED
}
