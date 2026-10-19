package org.lucoenergia.conluz.infrastructure.admin.community.membership.register;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.membership.register.ImportedMemberRow;
import org.lucoenergia.conluz.domain.admin.community.membership.register.ImportedMemberRow.ColumnViolation;
import org.lucoenergia.conluz.domain.admin.community.membership.register.ImportedMembers;
import org.lucoenergia.conluz.domain.admin.community.membership.register.MemberImportConstraint;
import org.lucoenergia.conluz.domain.admin.community.membership.register.MemberImportRowOutcome;
import org.lucoenergia.conluz.infrastructure.shared.i18n.I18nConfiguration;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorCode;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ImportMembersResponseMapperTest {

    private static final Locale SPANISH = Locale.forLanguageTag("es");

    private final ImportMembersResponseMapper mapper =
            new ImportMembersResponseMapper(new I18nConfiguration().messageSource());

    @Test
    @DisplayName("MEM-030 the response carries the counts and one result per row, in order, with each outcome's code")
    void eachOutcome_isMappedToItsCode() {
        ImportMembersResponse response = mapper.toResponse(new ImportedMembers(List.of(
                ImportedMemberRow.of(2, MemberImportRowOutcome.REGISTERED),
                ImportedMemberRow.of(3, MemberImportRowOutcome.ALREADY_MEMBER),
                invalid(4, ColumnViolation.of("fullName", MemberImportConstraint.REQUIRED)),
                ImportedMemberRow.of(5, MemberImportRowOutcome.MALFORMED))), Locale.ENGLISH);

        assertThat(response.getRegistered()).isEqualTo(1);
        assertThat(response.getFailed()).isEqualTo(3);
        assertThat(response.getRows())
                .extracting(ImportMembersRowResponse::getLine, ImportMembersRowResponse::getOutcome,
                        ImportMembersRowResponse::getCode)
                .containsExactly(
                        tuple(2, ImportMembersRowResponse.Outcome.REGISTERED, null),
                        tuple(3, ImportMembersRowResponse.Outcome.FAILED, RestErrorCode.MEMBERSHIP_ALREADY_EXISTS),
                        tuple(4, ImportMembersRowResponse.Outcome.FAILED, RestErrorCode.MEMBERSHIP_IMPORT_ROW_INVALID),
                        tuple(5, ImportMembersRowResponse.Outcome.FAILED,
                                RestErrorCode.MEMBERSHIP_IMPORT_ROW_MALFORMED));
        assertThat(response.getRows().get(0).getMessage()).isNull();
        assertThat(response.getRows()).filteredOn(row -> row.getLine() != 4)
                .allSatisfy(row -> assertThat(row.getErrors()).isEmpty());
    }

    @Test
    @DisplayName("MEM-030 an invalid row's errors carry each violation's column, constraint and parameters")
    void anInvalidRow_carriesItsErrors() {
        ImportMembersRowResponse row = mapper.toResponse(new ImportedMembers(List.of(invalid(2,
                new ColumnViolation("memberNumber", MemberImportConstraint.MIN, Map.of("min", "0")),
                ColumnViolation.of("contactEmail", MemberImportConstraint.EMAIL)))), Locale.ENGLISH).getRows().get(0);

        assertThat(row.getErrors())
                .extracting(ImportMembersRowErrorResponse::getColumn, ImportMembersRowErrorResponse::getConstraint,
                        ImportMembersRowErrorResponse::getParams)
                .containsExactly(
                        tuple("memberNumber", MemberImportConstraint.MIN, Map.of("min", "0")),
                        tuple("contactEmail", MemberImportConstraint.EMAIL, Map.of()));
    }

    @Test
    @DisplayName("MEM-030 every constraint has its own message, in English and in Spanish")
    void everyConstraint_hasItsMessage() {
        ImportedMemberRow row = invalid(2,
                ColumnViolation.of("personalId", MemberImportConstraint.REQUIRED),
                ColumnViolation.of("personalId", MemberImportConstraint.NOT_EMPTY_ONCE_NORMALISED),
                new ColumnViolation("memberNumber", MemberImportConstraint.MIN, Map.of("min", "0")),
                ColumnViolation.of("contactEmail", MemberImportConstraint.EMAIL),
                ColumnViolation.of("memberNumber", MemberImportConstraint.NOT_AN_INTEGER));

        assertThat(messageOf(row, Locale.ENGLISH)).isEqualTo("The row has invalid values: personalId is required; "
                + "personalId must contain a character other than spaces, dots and hyphens; memberNumber must be 0 "
                + "or greater; contactEmail must be an email address; memberNumber must be a whole number.");
        assertThat(messageOf(row, SPANISH)).isEqualTo("La fila tiene valores no válidos: personalId es obligatorio; "
                + "personalId debe contener algún carácter que no sea un espacio, un punto o un guion; memberNumber "
                + "debe ser 0 o mayor; contactEmail debe ser una dirección de correo electrónico; memberNumber debe "
                + "ser un número entero.");
    }

    @Test
    @DisplayName("MEM-030 the messages of an existing member and of a malformed row are in the caller's language")
    void otherOutcomes_areLocalised() {
        assertThat(messageOf(ImportedMemberRow.of(2, MemberImportRowOutcome.ALREADY_MEMBER), SPANISH))
                .isEqualTo("Esta persona ya es miembro de la comunidad, posiblemente por una fila anterior del fichero.");
        assertThat(messageOf(ImportedMemberRow.of(2, MemberImportRowOutcome.MALFORMED), Locale.ENGLISH))
                .isEqualTo("The row does not have as many values as the header has columns.");
    }

    @Test
    @DisplayName("ACC-010 a row with an invalid personal ID has the code USER_PERSONAL_ID_INVALID, its message in the "
            + "caller's language and its INVALID_PERSONAL_ID error")
    void anInvalidPersonalId_hasItsCodeMessageAndError() {
        ImportedMemberRow invalidPersonalId = new ImportedMemberRow(2, MemberImportRowOutcome.INVALID_PERSONAL_ID,
                List.of(ColumnViolation.of("personalId", MemberImportConstraint.INVALID_PERSONAL_ID)));

        ImportMembersRowResponse row = mapper.toResponse(new ImportedMembers(List.of(invalidPersonalId)),
                Locale.ENGLISH).getRows().get(0);

        assertThat(row.getOutcome()).isEqualTo(ImportMembersRowResponse.Outcome.FAILED);
        assertThat(row.getCode()).isEqualTo(RestErrorCode.USER_PERSONAL_ID_INVALID);
        assertThat(row.getErrors())
                .extracting(ImportMembersRowErrorResponse::getColumn, ImportMembersRowErrorResponse::getConstraint,
                        ImportMembersRowErrorResponse::getParams)
                .containsExactly(tuple("personalId", MemberImportConstraint.INVALID_PERSONAL_ID, Map.of()));
        assertThat(row.getMessage()).isEqualTo("The DNI/NIE/NIF is not valid. Check the digits and the letter.");
        assertThat(messageOf(invalidPersonalId, SPANISH))
                .isEqualTo("El DNI/NIE/NIF no es válido. Revisa los números y la letra.");
    }

    @Test
    @DisplayName("ACC-010 the constraint INVALID_PERSONAL_ID has its own message, in English and in Spanish")
    void theInvalidPersonalIdConstraint_hasItsMessage() {
        ImportedMemberRow row = invalid(2, ColumnViolation.of("personalId", MemberImportConstraint.INVALID_PERSONAL_ID));

        assertThat(messageOf(row, Locale.ENGLISH))
                .isEqualTo("The row has invalid values: personalId is not a valid DNI/NIE/NIF.");
        assertThat(messageOf(row, SPANISH))
                .isEqualTo("La fila tiene valores no válidos: personalId no es un DNI/NIE/NIF válido.");
    }

    private String messageOf(ImportedMemberRow row, Locale locale) {
        return mapper.toResponse(new ImportedMembers(List.of(row)), locale).getRows().get(0).getMessage();
    }

    private static ImportedMemberRow invalid(int line, ColumnViolation... violations) {
        return new ImportedMemberRow(line, MemberImportRowOutcome.INVALID, List.of(violations));
    }
}
