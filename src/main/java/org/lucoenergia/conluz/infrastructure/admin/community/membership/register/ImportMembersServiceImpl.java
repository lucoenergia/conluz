package org.lucoenergia.conluz.infrastructure.admin.community.membership.register;

import jakarta.validation.Validator;
import org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.community.membership.register.ImportMembersService;
import org.lucoenergia.conluz.domain.admin.community.membership.register.ImportedMemberRow;
import org.lucoenergia.conluz.domain.admin.community.membership.register.ImportedMemberRow.ColumnViolation;
import org.lucoenergia.conluz.domain.admin.community.membership.register.ImportedMembers;
import org.lucoenergia.conluz.domain.admin.community.membership.register.MemberImportFileRefusedException;
import org.lucoenergia.conluz.domain.admin.community.membership.register.MemberImportRowOutcome;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.validation.DataBinder;
import org.springframework.validation.FieldError;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Not transactional itself: each row is registered in a transaction of its own by {@link MemberImportRowStep}, so a
 * row that fails keeps the rows registered before and after it, and the locks are held for one row at a time.
 *
 * <p>Each row is bound to a {@link RegisterMemberBody} through its setters and validated with its constraints, as a
 * registration request is, so the import and the registration accept the same values and record them the same way.
 * </p>
 */
@Service
public class ImportMembersServiceImpl implements ImportMembersService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportMembersServiceImpl.class);

    private final MemberImportCsvReader reader;
    private final MemberImportRowStep step;
    private final SpringValidatorAdapter validator;
    private final MessageSource messageSource;

    public ImportMembersServiceImpl(MemberImportCsvReader reader, MemberImportRowStep step, Validator validator,
                                    MessageSource messageSource) {
        this.reader = reader;
        this.step = step;
        this.validator = new SpringValidatorAdapter(validator);
        this.messageSource = messageSource;
    }

    @Override
    public ImportedMembers importMembers(UUID communityId, UserId importedBy, byte[] csv) {
        List<MemberImportCsvRow> rows;
        try {
            rows = reader.read(csv);
        } catch (MemberImportFileRefusedException e) {
            // Without the headers: a file without a header line holds a member's data in its first line
            LOGGER.info("Member import refused: communityId={}, reason={}", communityId, e.getProblem());
            throw e;
        }

        List<ImportedMemberRow> results = new ArrayList<>();
        for (MemberImportCsvRow row : rows) {
            results.add(importRow(communityId, importedBy, row));
        }
        ImportedMembers imported = new ImportedMembers(results);

        LOGGER.info("Members imported: communityId={}, registered={}, failed={}", communityId, imported.registered(),
                imported.failed());
        return imported;
    }

    private ImportedMemberRow importRow(UUID communityId, UserId importedBy, MemberImportCsvRow row) {
        if (row.isMalformed()) {
            return ImportedMemberRow.of(row.line(), MemberImportRowOutcome.MALFORMED);
        }

        RegisterMemberBody body = new RegisterMemberBody();
        DataBinder binder = new DataBinder(body);
        binder.setValidator(validator);
        binder.bind(new MutablePropertyValues(row.values()));
        binder.validate();
        if (binder.getBindingResult().hasFieldErrors()) {
            return new ImportedMemberRow(row.line(), MemberImportRowOutcome.INVALID,
                    violationsOf(binder.getBindingResult().getFieldErrors()));
        }

        try {
            step.register(communityId, importedBy, body.toPersonalId(), body.toMemberRecord());
            return ImportedMemberRow.of(row.line(), MemberImportRowOutcome.REGISTERED);
        } catch (MembershipAlreadyExistsException e) {
            return ImportedMemberRow.of(row.line(), MemberImportRowOutcome.ALREADY_MEMBER);
        }
    }

    /**
     * One per broken rule, in the order of the columns, so the same row always reads the same way. A value that
     * cannot be converted is described without the conversion error, which would repeat the value.
     */
    private List<ColumnViolation> violationsOf(List<FieldError> errors) {
        List<ColumnViolation> violations = new ArrayList<>();
        errors.stream()
                .sorted(Comparator.comparingInt((FieldError error) ->
                                MemberImportCsvReader.COLUMNS.indexOf(error.getField()))
                        .thenComparing(error -> String.valueOf(error.getDefaultMessage())))
                .forEach(error -> {
                    String reason = error.isBindingFailure()
                            ? messageSource.getMessage("error.membership.import.row.not.a.whole.number", null,
                            LocaleContextHolder.getLocale())
                            : error.getDefaultMessage();
                    violations.add(new ColumnViolation(error.getField(), reason));
                });
        return violations;
    }
}
