package org.lucoenergia.conluz.infrastructure.shared.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebConfig is a configuration class that implements the {@link WebMvcConfigurer} interface
 * to customize the behavior of Spring MVC. It is marked with {@link Configuration}, indicating
 * that it defines bean configurations and settings for the application context.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Configures content negotiation settings for the application.
     *
     * @param configurer the {@link ContentNegotiationConfigurer} used to customize content negotiation settings
     */
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer
                .ignoreAcceptHeader(true) // Ignore the Accept header for content negotiation
                .favorParameter(false) // Disable content type resolution through a parameter
                .defaultContentType(MediaType.APPLICATION_JSON); // Set JSON as the default content type
    }
}