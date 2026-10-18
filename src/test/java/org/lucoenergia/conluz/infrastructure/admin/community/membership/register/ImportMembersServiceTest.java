package org.lucoenergia.conluz.infrastructure.admin.community.membership.register;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.community.membership.MemberRecord;
import org.lucoenergia.conluz.domain.admin.community.membership.register.ImportedMemberRow;
import org.lucoenergia.conluz.domain.admin.community.membership.register.ImportedMemberRow.ColumnViolation;
import org.lucoenergia.conluz.domain.admin.community.membership.register.ImportedMembers;
import org.lucoenergia.conluz.domain.admin.community.membership.register.MemberImportFileProblem;
import org.lucoenergia.conluz.domain.admin.community.membership.register.MemberImportFileRefusedException;
import org.lucoenergia.conluz.domain.admin.community.membership.register.MemberImportRowOutcome;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportMembersServiceTest {

    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();

    private static final UUID COMMUNITY_ID = UUID.randomUUID();
    private static final UserId ADMIN = UserId.of(UUID.randomUUID());
    private static final byte[] FILE = new byte[]{1};

    @Mock
    private MemberImportCsvReader reader;
    @Mock
    private MemberImportRowStep step;

    private ImportMembersServiceImpl service;

    @BeforeEach
    void setUp() {
        Validator validator = VALIDATORS.getValidator();
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("error.membership.import.row.not.a.whole.number", Locale.getDefault(),
                "must be a whole number");
        messageSource.setUseCodeAsDefaultMessage(true);
        service = new ImportMembersServiceImpl(reader, step, validator, messageSource);
    }

    @AfterAll
    static void closeValidators() {
        VALIDATORS.close();
    }

    @Test
    @DisplayName("MEM-030 each row is registered as a member registration body would be, in the order of the file")
    void eachRow_isRegisteredInOrder() {
        when(reader.read(FILE)).thenReturn(List.of(
                row(2, Map.of("personalId", "1", "fullName", "Ana", "memberNumber", "7", "contactEmail", "  ",
                        "address", "Calle", "phoneNumber", "")),
                row(3, Map.of("personalId", "2", "fullName", "Luis"))));

        ImportedMembers imported = service.importMembers(COMMUNITY_ID, ADMIN, FILE);

        assertThat(imported.rows()).extracting(ImportedMemberRow::line, ImportedMemberRow::outcome).containsExactly(
                tuple(2, MemberImportRowOutcome.REGISTERED),
                tuple(3, MemberImportRowOutcome.REGISTERED));
        assertThat(imported.registered()).isEqualTo(2);
        assertThat(imported.failed()).isZero();
        InOrder order = inOrder(step);
        order.verify(step).register(eq(COMMUNITY_ID), eq(ADMIN), personalId("1"),
                eq(new MemberRecord("Ana", 7, null, "Calle", null)));
        order.verify(step).register(eq(COMMUNITY_ID), eq(ADMIN), personalId("2"),
                eq(new MemberRecord("Luis", null, null, null, null)));
    }

    @Test
    @DisplayName("MEM-030 a person already in the community is a row error, and the following rows are registered")
    void anExistingMember_isARowError_andTheImportGoesOn() {
        when(reader.read(FILE)).thenReturn(List.of(
                row(2, Map.of("personalId", "1", "fullName", "Ana")),
                row(3, Map.of("personalId", "2", "fullName", "Luis"))));
        doThrow(new MembershipAlreadyExistsException(COMMUNITY_ID, UUID.randomUUID()))
                .when(step).register(eq(COMMUNITY_ID), eq(ADMIN), personalId("1"), any());

        ImportedMembers imported = service.importMembers(COMMUNITY_ID, ADMIN, FILE);

        assertThat(imported.rows()).extracting(ImportedMemberRow::outcome)
                .containsExactly(MemberImportRowOutcome.ALREADY_MEMBER, MemberImportRowOutcome.REGISTERED);
        assertThat(imported.registered()).isEqualTo(1);
        assertThat(imported.failed()).isEqualTo(1);
    }

    @Test
    @DisplayName("MEM-030 an invalid row names its columns and rules, never its values, and is not registered")
    void anInvalidRow_namesItsColumns_andIsNotRegistered() {
        when(reader.read(FILE)).thenReturn(List.of(
                row(2, Map.of("personalId", ".-.", "fullName", " ", "memberNumber", "-1",
                        "contactEmail", "not-an-email"))));

        ImportedMembers imported = service.importMembers(COMMUNITY_ID, ADMIN, FILE);

        ImportedMemberRow result = imported.rows().get(0);
        assertThat(result.outcome()).isEqualTo(MemberImportRowOutcome.INVALID);
        assertThat(result.violations()).extracting(ColumnViolation::column)
                .containsExactly("personalId", "fullName", "memberNumber", "contactEmail");
        assertThat(result.violations()).extracting(ColumnViolation::reason)
                .noneMatch(reason -> reason.contains("not-an-email") || reason.contains("-1"));
        verify(step, never()).register(any(), any(), any(), any());
    }

    @Test
    @DisplayName("MEM-030 a member number that is not a whole number is described without repeating it")
    void aMemberNumberThatIsNotAWholeNumber_isInvalid() {
        when(reader.read(FILE)).thenReturn(List.of(
                row(2, Map.of("personalId", "1", "fullName", "Ana", "memberNumber", "7b"))));

        ImportedMemberRow result = service.importMembers(COMMUNITY_ID, ADMIN, FILE).rows().get(0);

        assertThat(result.outcome()).isEqualTo(MemberImportRowOutcome.INVALID);
        assertThat(result.violations()).containsExactly(new ColumnViolation("memberNumber", "must be a whole number"));
        verify(step, never()).register(any(), any(), any(), any());
    }

    @Test
    @DisplayName("MEM-030 a malformed row is a row error and is not registered")
    void aMalformedRow_isARowError() {
        when(reader.read(FILE)).thenReturn(List.of(MemberImportCsvRow.malformed(4)));

        ImportedMembers imported = service.importMembers(COMMUNITY_ID, ADMIN, FILE);

        assertThat(imported.rows()).containsExactly(ImportedMemberRow.of(4, MemberImportRowOutcome.MALFORMED));
        verify(step, never()).register(any(), any(), any(), any());
    }

    @Test
    @DisplayName("MEM-030 a refused file registers nothing")
    void aRefusedFile_registersNothing() {
        when(reader.read(FILE)).thenThrow(new MemberImportFileRefusedException(MemberImportFileProblem.TOO_MANY_ROWS));

        assertThatThrownBy(() -> service.importMembers(COMMUNITY_ID, ADMIN, FILE))
                .isInstanceOf(MemberImportFileRefusedException.class);
        verify(step, never()).register(any(), any(), any(), any());
    }

    @Test
    @DisplayName("MEM-030 an unexpected failure is not turned into a row error")
    void anUnexpectedFailure_isNotARowError() {
        when(reader.read(FILE)).thenReturn(List.of(row(2, Map.of("personalId", "1", "fullName", "Ana"))));
        doThrow(new CommunityNotFoundException(COMMUNITY_ID)).when(step).register(any(), any(), any(), any());

        assertThatThrownBy(() -> service.importMembers(COMMUNITY_ID, ADMIN, FILE))
                .isInstanceOf(CommunityNotFoundException.class);
    }

    private static UserPersonalId personalId(String value) {
        return argThat(personalId -> value.equals(personalId.getPersonalId()));
    }

    private static MemberImportCsvRow row(int line, Map<String, String> values) {
        return MemberImportCsvRow.of(line, values);
    }
}
