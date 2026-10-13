package org.lucoenergia.conluz.domain.production.huawei.config;

import java.util.Optional;

/**
 * A request to set a plant's Huawei configuration.
 *
 * @param password the new password, or empty when the request does not supply one, in which case the
 *                 stored password is kept (#399)
 */
public record SetHuaweiConfigurationCommand(String username, Optional<String> password, String baseUrl,
                                            Boolean enabled) {
}
