package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import org.lucoenergia.conluz.domain.admin.supply.create.ImportedSupplyRow;
import org.lucoenergia.conluz.domain.shared.response.CreationInBulkResponse;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class ImportSuppliesResponseMapper {

    private final MessageSource messageSource;

    public ImportSuppliesResponseMapper(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public CreationInBulkResponse<String, String> toResponse(List<ImportedSupplyRow> rows, Locale locale) {
        CreationInBulkResponse<String, String> response = new CreationInBulkResponse<>();
        for (ImportedSupplyRow row : rows) {
            switch (row.outcome()) {
                case CREATED -> response.addCreated(row.code());
                case ALREADY_EXISTS -> response.addError(row.code(), messageSource.getMessage(
                        "error.supply.already.exists", new Object[]{row.code()}, locale));
                case USER_NOT_FOUND -> response.addError(row.code(), messageSource.getMessage(
                        "error.user.not.found", new Object[]{row.personalId()}, locale));
                case FAILED -> response.addError(row.code(), messageSource.getMessage(
                        "error.supply.unable.to.create", new Object[]{}, locale));
            }
        }
        return response;
    }
}
