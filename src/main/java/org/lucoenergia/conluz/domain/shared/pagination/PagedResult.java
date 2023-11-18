package org.lucoenergia.conluz.domain.shared.pagination;

import java.util.ArrayList;
import java.util.List;

public class PagedResult<T> {

    private final List<T> items;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final int number;

    public PagedResult(List<T> items, int size, long totalElements, int totalPages, int number) {
        this.items = items;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.number = number;
    }

    public List<T> getItems() {
        return new ArrayList<>(items);
    }

    public int getSize() {
        return size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public int getNumber() {
        return number;
    }
}
