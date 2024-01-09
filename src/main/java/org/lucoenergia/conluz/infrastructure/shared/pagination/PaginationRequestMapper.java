package org.lucoenergia.conluz.infrastructure.shared.pagination;

import org.lucoenergia.conluz.domain.shared.pagination.Direction;
import org.lucoenergia.conluz.domain.shared.pagination.Order;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaginationRequestMapper {

    public PagedRequest mapRequest(Pageable page) {
        if (page.getSort().isSorted()) {
            List<Order> orders = Sort.by(page.getSort().toList()).stream()
                    .map( order -> new Order(mapDirection(order.getDirection()), order.getProperty()))
                    .toList();
            return PagedRequest.of(page.getPageNumber(), page.getPageSize(), orders);
        }
        return PagedRequest.of(page.getPageNumber(), page.getPageSize());
    }

    public Pageable mapRequest(PagedRequest pagedRequest) {
        if (pagedRequest.isSorted()) {
            Sort sort = Sort.by(pagedRequest.getOrders().stream()
                    .map( order -> new Sort.Order(mapDirection(order.getDirection()), order.getProperty()))
                    .toList()
            );
            return PageRequest.of(pagedRequest.getPage(), pagedRequest.getSize(), sort);
        }
        return PageRequest.of(pagedRequest.getPage(), pagedRequest.getSize());
    }

    private Sort.Direction mapDirection(Direction direction) {
        return switch (direction) {
            case ASC -> Sort.Direction.ASC;
            case DESC -> Sort.Direction.DESC;
        };
    }

    private Direction mapDirection(Sort.Direction direction) {
        return switch (direction) {
            case ASC -> Direction.ASC;
            case DESC -> Direction.DESC;
        };
    }
}
