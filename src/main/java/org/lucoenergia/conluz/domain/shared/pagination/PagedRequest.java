package org.lucoenergia.conluz.domain.shared.pagination;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PagedRequest {

    public static final int FIRST_PAGE = 0;
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final Integer page;
    private final Integer size;
    private final List<Order> orders;

    protected PagedRequest(Integer page, Integer size, List<Order> orders) {
        this.page = page != null ? page : FIRST_PAGE;
        this.size = size != null ? size : DEFAULT_PAGE_SIZE;
        this.orders = orders != null ? orders : new ArrayList<>();
    }

    public static PagedRequest of(Integer page, Integer size) {
        return of(page, size, Collections.emptyList());
    }

    public static PagedRequest of(Integer page, Integer size, List<Order> orders) {
        if (orders == null) {
            orders = new ArrayList<>();
        }
        return new PagedRequest(page, size, orders);
    }

    public boolean isSorted() {
        return !orders.isEmpty();
    }

    public Integer getPage() {
        return page;
    }

    public Integer getSize() {
        return size;
    }

    public List<Order> getOrders() {
        return new ArrayList<>(orders);
    }
}
