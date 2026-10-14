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
 * The disabled-integration messages name, in both shipped locales, the current operation that enables the
 * integration: the provider's enable action, or for Shelly, which has none, its configuration write (#414).
 */
class DisabledIntegrationMessagesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "messages.properties    | error.huawei.disabled  | POST /api/v1/plants/{plantId}/production/huawei/config/enable",
            "messages_es.properties | error.huawei.disabled  | POST /api/v1/plants/{plantId}/production/huawei/config/enable",
            "messages.properties    | error.datadis.disabled | POST /api/v1/communities/{communityId}/config/datadis/enable",
            "messages_es.properties | error.datadis.disabled | POST /api/v1/communities/{communityId}/config/datadis/enable",
            "messages.properties    | error.shelly.disabled  | PUT /api/v1/communities/{communityId}/config/shelly",
            "messages_es.properties | error.shelly.disabled  | PUT /api/v1/communities/{communityId}/config/shelly"
    })
    void eachLocaleNamesTheOperationThatEnablesIt(String bundle, String key, String operation) throws IOException {
        Properties messages = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(bundle)) {
            messages.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        assertThat(messages.getProperty(key)).contains(operation);
    }
}
