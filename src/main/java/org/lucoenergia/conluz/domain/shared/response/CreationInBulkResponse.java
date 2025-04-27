package org.lucoenergia.conluz.domain.shared.response;

import org.lucoenergia.conluz.domain.shared.error.BulkError;

import java.util.ArrayList;
import java.util.List;

public class CreationInBulkResponse<E, I> {

    private final List<I> created = new ArrayList<>();
    private final List<BulkError<E>> errors = new ArrayList<>();

    public void addCreated(I item) {
        created.add(item);
    }

    public void addError(E element, String errorMessage) {
        errors.add(new BulkError<>(element, errorMessage));
    }

    public List<I> getCreated() {
        return created.stream().toList();
    }

    public List<BulkError<E>> getErrors() {
        return new ArrayList<>(errors);
    }
}
