package org.lucoenergia.conluz.infrastructure.admin.community.membership.register;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.metadata.ConstraintDescriptor;
import org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.community.membership.register.ImportMembersService;
import org.lucoenergia.conluz.domain.admin.community.membership.register.ImportedMemberRow;
import org.lucoenergia.conluz.domain.admin.community.membership.register.ImportedMemberRow.ColumnViolation;
import org.lucoenergia.conluz.domain.admin.community.membership.register.ImportedMembers;
import org.lucoenergia.conluz.domain.admin.community.membership.register.MemberImportConstraint;
import org.lucoenergia.conluz.domain.admin.community.membership.register.MemberImportFileRefusedException;
import org.lucoenergia.conluz.domain.admin.community.membership.register.MemberImportRowOutcome;
import org.lucoenergia.conluz.domain.admin.user.UserPersonalIdInvalidException;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.personalid.NotEmptyOnceNormalised;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.stereotype.Service;
import org.springframework.validation.DataBinder;
import org.springframework.validation.FieldError;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Not transactional itself: each row is registered in a transaction of its own by {@link MemberImportRowStep}, so a
 * row that fails keeps the rows registered before and after it, and the locks are held for one row at a time.
 *
 * <p>Each row is bound to a {@link RegisterMemberBody} through its setters and validated with its constraints, as a
 * registration request is, so the import and the registration accept the same values and record them the same way.
 * A broken rule is reported as its {@link MemberImportConstraint}, never with the validator's message, which is in the
 * server's language and not the caller's.</p>
 */
@Service
public class ImportMembersServiceImpl implements ImportMembersService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportMembersServiceImpl.class);

    /**
     * Every constraint of {@link RegisterMemberBody}, by its annotation.
     */
    static final Map<Class<? extends Annotation>, MemberImportConstraint> CONSTRAINTS = Map.of(
            NotBlank.class, MemberImportConstraint.REQUIRED,
            NotEmptyOnceNormalised.class, MemberImportConstraint.NOT_EMPTY_ONCE_NORMALISED,
            Min.class, MemberImportConstraint.MIN,
            Email.class, MemberImportConstraint.EMAIL);

    private static final String PERSONAL_ID_COLUMN = "personalId";

    private final MemberImportCsvReader reader;
    private final MemberImportRowStep step;
    private final SpringValidatorAdapter validator;

    public ImportMembersServiceImpl(MemberImportCsvReader reader, MemberImportRowStep step, Validator validator) {
        this.reader = reader;
        this.step = step;
        this.validator = new SpringValidatorAdapter(validator);
    }

    @Override
    public ImportedMembers importMembers(UUID communityId, UserId importedBy, byte[] csv) {
        MemberImportCsv file;
        try {
            file = reader.read(csv);
        } catch (MemberImportFileRefusedException e) {
            // Without the headers: a file without a header line holds a member's data in its first line
            LOGGER.info("Member import refused: communityId={}, reason={}", communityId, e.getProblem());
            throw e;
        }

        List<ImportedMemberRow> results = new ArrayList<>();
        for (MemberImportCsvRow row : file.rows()) {
            results.add(importRow(communityId, importedBy, row));
        }
        ImportedMembers imported = new ImportedMembers(results);

        LOGGER.info("Members imported: communityId={}, registered={}, failed={}, encoding={}", communityId,
                imported.registered(), imported.failed(), file.encoding().label());
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
        } catch (UserPersonalIdInvalidException e) {
            return new ImportedMemberRow(row.line(), MemberImportRowOutcome.INVALID_PERSONAL_ID,
                    List.of(ColumnViolation.of(PERSONAL_ID_COLUMN, MemberImportConstraint.INVALID_PERSONAL_ID)));
        }
    }

    /**
     * One per broken rule, in the order of the columns and then of the rules, so the same row always reads the same
     * way.
     */
    private static List<ColumnViolation> violationsOf(List<FieldError> errors) {
        return errors.stream()
                .map(ImportMembersServiceImpl::violationOf)
                .sorted(Comparator.comparingInt((ColumnViolation violation) ->
                                MemberImportCsvReader.COLUMNS.indexOf(violation.column()))
                        .thenComparing(ColumnViolation::constraint))
                .toList();
    }

    private static ColumnViolation violationOf(FieldError error) {
        // The only value that is converted is the member number, so a value that cannot be is not a whole number
        if (error.isBindingFailure()) {
            return ColumnViolation.of(error.getField(), MemberImportConstraint.NOT_AN_INTEGER);
        }
        ConstraintDescriptor<?> descriptor = error.unwrap(ConstraintViolation.class).getConstraintDescriptor();
        Annotation annotation = descriptor.getAnnotation();
        MemberImportConstraint constraint = CONSTRAINTS.get(annotation.annotationType());
        if (constraint == null) {
            // A constraint added to the registration body without a code; a test checks that each one has a code
            throw new IllegalStateException("No import constraint for " + annotation.annotationType().getName());
        }
        if (annotation instanceof Min min) {
            return new ColumnViolation(error.getField(), constraint, Map.of("min", String.valueOf(min.value())));
        }
        return ColumnViolation.of(error.getField(), constraint);
    }
}
