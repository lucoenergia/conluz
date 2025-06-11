package org.lucoenergia.conluz.domain.shared.error;

public class BulkError<T> {
    private final T item;
    private final String errorMessage;

    public BulkError(T item, String errorMessage) {
        this.item = item;
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public T getItem() {
        return item;
    }
}