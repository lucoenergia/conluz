package org.lucoenergia.conluz.infrastructure.shared.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the settings of the web client as the public sees it.
 *
 * <p>Deliberately not validated at binding time: a missing or invalid URL must not stop the application from
 * starting. Whoever builds links from it checks it instead.</p>
 *
 * @param publicUrl the address members open the web client at, such as {@code https://app.example.org}; empty when
 *                  not configured
 */
@ConfigurationProperties(prefix = "conluz.web")
public record PublicWebProperties(String publicUrl) {
}
