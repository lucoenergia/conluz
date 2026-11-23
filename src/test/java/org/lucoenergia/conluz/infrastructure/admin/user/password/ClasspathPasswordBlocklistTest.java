package org.lucoenergia.conluz.infrastructure.admin.user.password;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClasspathPasswordBlocklistTest {

    private static final String SECLISTS = "password-blocklist/seclists-top-1m-min10.txt";
    private static final String CURATED = "password-blocklist/curated-es.txt";

    private static ClasspathPasswordBlocklist bundled;

    @BeforeAll
    static void loadTheBundledLists() {
        bundled = new ClasspathPasswordBlocklist(PasswordPolicyConfiguration.BLOCKLIST_RESOURCES);
    }

    @Test
    @DisplayName("ACC-022 the bundled lists contain an entry of the curated list")
    void theBundledListsContainACuratedEntry() {
        assertThat(bundled.contains("lucoenergia1")).isTrue();
    }

    @Test
    @DisplayName("ACC-022 the bundled lists contain a common password once the caller lowercases it")
    void theBundledListsContainACommonPasswordOnceLowercased() {
        assertThat(bundled.contains("QWERTYUIOP".toLowerCase(Locale.ROOT))).isTrue();
    }

    @Test
    @DisplayName("ACC-022 the bundled lists do not contain a random password")
    void theBundledListsDoNotContainARandomPassword() {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 20);

        assertThat(bundled.contains(random)).isFalse();
    }

    @Test
    void theApplicationUsesBothBundledLists() {
        assertThat(PasswordPolicyConfiguration.BLOCKLIST_RESOURCES).containsExactlyInAnyOrder(SECLISTS, CURATED);
    }

    @Test
    void eachListIsReadOnItsOwn() {
        assertThat(new ClasspathPasswordBlocklist(List.of(CURATED)).contains("lucoenergia1")).isTrue();
        assertThat(new ClasspathPasswordBlocklist(List.of(SECLISTS)).contains("lucoenergia1")).isFalse();
    }

    @Test
    void aMissingListFailsTheConstruction() {
        assertThatThrownBy(() -> new ClasspathPasswordBlocklist(List.of(CURATED, "password-blocklist/missing.txt")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("password-blocklist/missing.txt")
                .hasMessageEndingWith(" is missing");
    }

    @Test
    void anEmptyListFailsTheConstruction() {
        String empty = "fixtures/password-blocklist/empty.txt";

        assertThatThrownBy(() -> new ClasspathPasswordBlocklist(List.of(CURATED, empty)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(empty)
                .hasMessageEndingWith(" is empty");
    }
}
