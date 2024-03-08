package org.lucoenergia.conluz.infrastructure.admin.user.create;

import org.lucoenergia.conluz.domain.shared.UserPersonalId;

import java.util.ArrayList;
import java.util.List;

public class CreateUsersInBulkResponse {

    private final List<UserPersonalId> created = new ArrayList<>();
    private final List<CreateUsersInBulkError> errors = new ArrayList<>();

    public void addCreated(UserPersonalId personalId) {
        created.add(personalId);
    }
    public void addError(UserPersonalId personalId, String errorMessage) {
        errors.add(new CreateUsersInBulkError(personalId, errorMessage));
    }

    public List<String> getCreated() {
        return created.stream().map(UserPersonalId::getPersonalId).toList();
    }

    public List<CreateUsersInBulkError> getErrors() {
        return new ArrayList<>(errors);
    }

    public static class CreateUsersInBulkError {
        private final UserPersonalId personalId;
        private final String errorMessage;

        public CreateUsersInBulkError(UserPersonalId personalId, String errorMessage) {
            this.personalId = personalId;
            this.errorMessage = errorMessage;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
        public String getPersonalId() {
            return personalId.getPersonalId();
        }
    }
}
