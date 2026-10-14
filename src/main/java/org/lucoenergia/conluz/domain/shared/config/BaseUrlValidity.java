package org.lucoenergia.conluz.domain.shared.config;

/**
 * Whether a string is a valid base URL for a provider's API: the same verdict the configuration write gives
 * the {@code baseUrl} it receives, so data the write would reject is never considered valid elsewhere.
 */
public interface BaseUrlValidity {

    boolean isValid(String baseUrl);
}
