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

    USER_LAST_PLATFORM_ADMIN
}
