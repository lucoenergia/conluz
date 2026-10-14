package org.lucoenergia.conluz.domain.shared.config;

import java.util.stream.Stream;

/**
 * Base URLs whose verdict the configuration writes give, shared by every test that checks another path gives the
 * same verdict (#414). Each list is what the writes' {@code @NotBlank @URL} actually decides, odd cases included,
 * not what a valid URL ought to be.
 */
public final class BaseUrlFixtures {

    private BaseUrlFixtures() {
    }

    public static Stream<String> rejected() {
        return Stream.of(
                "",
                " ",
                "not-a-url",
                "example.com/thirdData",
                "htp://example.com",
                "https//example.com"
        );
    }

    public static Stream<String> accepted() {
        return Stream.of(
                "https://datadis.es",
                "http://localhost:8000/thirdData",
                "ftp://example.com",
                // java.net.URL accepts an empty authority, and so does @URL
                "http://"
        );
    }
}
