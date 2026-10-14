package org.lucoenergia.conluz.domain.admin.community.membership;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemberRecordTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", " \t\n\r\f\u000B"})
    @DisplayName("MEM-007 an empty or blank optional value is recorded as none")
    void anEmptyOrBlankOptionalValue_isRecordedAsNone(String value) {
        MemberRecord record = new MemberRecord("Lucía", null, value, value, value);

        assertThat(record.contactEmail()).isNull();
        assertThat(record.address()).isNull();
        assertThat(record.phoneNumber()).isNull();
        assertThat(record.hasContactEmail()).isFalse();
    }

    @Test
    @DisplayName("MEM-007 every value is kept exactly as entered, untrimmed")
    void everyValue_isKeptAsEntered() {
        MemberRecord record = new MemberRecord(" Lucía ", 7, " contact@example.org", " Calle Mayor 1 ", " 600 ");

        assertThat(record.fullName()).isEqualTo(" Lucía ");
        assertThat(record.memberNumber()).isEqualTo(7);
        assertThat(record.contactEmail()).isEqualTo(" contact@example.org");
        assertThat(record.address()).isEqualTo(" Calle Mayor 1 ");
        assertThat(record.phoneNumber()).isEqualTo(" 600 ");
        assertThat(record.hasContactEmail()).isTrue();
    }
}
