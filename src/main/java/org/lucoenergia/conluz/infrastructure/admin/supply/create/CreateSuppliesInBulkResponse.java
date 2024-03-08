package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import org.lucoenergia.conluz.domain.shared.SupplyCode;

import java.util.ArrayList;
import java.util.List;

public class CreateSuppliesInBulkResponse {

    private final List<SupplyCode> created = new ArrayList<>();
    private final List<CreateSuppliesInBulkError> errors = new ArrayList<>();

    public void addCreated(SupplyCode code) {
        created.add(code);
    }
    public void addError(SupplyCode code, String errorMessage) {
        errors.add(new CreateSuppliesInBulkError(code, errorMessage));
    }

    public List<String> getCreated() {
        return created.stream().map(SupplyCode::getCode).toList();
    }

    public List<CreateSuppliesInBulkError> getErrors() {
        return new ArrayList<>(errors);
    }

    public static class CreateSuppliesInBulkError {
        private final SupplyCode supply;
        private final String errorMessage;

        public CreateSuppliesInBulkError(SupplyCode supply, String errorMessage) {
            this.supply = supply;
            this.errorMessage = errorMessage;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
        public String getSupply() {
            return supply.getCode();
        }
    }
}
