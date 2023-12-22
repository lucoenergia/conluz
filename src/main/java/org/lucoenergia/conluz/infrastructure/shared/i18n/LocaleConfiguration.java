package org.lucoenergia.conluz.infrastructure.shared.i18n;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.Locale;

@Configuration
public class LocaleConfiguration {

    @Value("${conluz.i18n.locale.default}")
    private String defaultLocale;

    public String getDefaultLocale() {
        return defaultLocale;
    }

    @PostConstruct
    public void setDefaultLocale() {
        final Locale locale = new Locale(defaultLocale);
        Locale.setDefault(locale);
    }

    @Bean
    public LocaleResolver localeResolver() {
        final Locale locale = new Locale(defaultLocale);
        AcceptHeaderLocaleResolver localeResolver = new AcceptHeaderLocaleResolver();
        localeResolver.setDefaultLocale(locale); // Set your preferred default locale here (e.g., English)
        return localeResolver;
    }
}
