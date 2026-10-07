package org.lucoenergia.conluz.infrastructure.shared.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Binds the email sending settings.
 *
 * <p>Deliberately not validated at binding time: a missing or invalid setting must not stop the application from
 * starting. {@link EmailConfiguration} checks them instead, and turns a misconfiguration into one warning at
 * startup. For the same reason the port is bound as text and parsed there.</p>
 */
@ConfigurationProperties(prefix = "conluz.mail")
public class EmailProperties {

    /**
     * Whether emails are sent at all. When off, no SMTP connection is ever opened.
     */
    private final boolean enabled;
    private final String host;
    private final String port;
    /**
     * Empty when the SMTP server needs no authentication.
     */
    private final String username;
    private final String password;
    private final String fromAddress;
    /**
     * The display name shown next to {@link #fromAddress}. Empty to show the address only.
     */
    private final String fromName;
    /**
     * Whether the connection must be upgraded with STARTTLS. When on, a server that does not offer it is refused.
     */
    private final boolean starttls;

    public EmailProperties(@DefaultValue("false") boolean enabled,
                           String host,
                           @DefaultValue("587") String port,
                           String username,
                           String password,
                           String fromAddress,
                           String fromName,
                           @DefaultValue("true") boolean starttls) {
        this.enabled = enabled;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.starttls = starttls;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getHost() {
        return host;
    }

    public String getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public String getFromName() {
        return fromName;
    }

    public boolean isStarttls() {
        return starttls;
    }

    /**
     * Names the settings only: their values include a credential and addresses.
     */
    @Override
    public String toString() {
        return "EmailProperties{enabled=" + enabled + ", starttls=" + starttls + '}';
    }
}
