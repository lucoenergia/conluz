package org.lucoenergia.conluz.infrastructure.shared.i18n;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The disabled-integration messages point, in both shipped locales, to the provider's enable action (#414).
 */
class DisabledIntegrationMessagesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "messages.properties    | error.huawei.disabled  | POST /api/v1/plants/{plantId}/production/huawei/config/enable",
            "messages_es.properties | error.huawei.disabled  | POST /api/v1/plants/{plantId}/production/huawei/config/enable",
            "messages.properties    | error.datadis.disabled | POST /api/v1/communities/{communityId}/config/datadis/enable",
            "messages_es.properties | error.datadis.disabled | POST /api/v1/communities/{communityId}/config/datadis/enable"
    })
    void eachLocaleNamesTheEnableAction(String bundle, String key, String enableAction) throws IOException {
        Properties messages = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(bundle)) {
            messages.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        assertThat(messages.getProperty(key)).contains(enableAction);
    }
}
