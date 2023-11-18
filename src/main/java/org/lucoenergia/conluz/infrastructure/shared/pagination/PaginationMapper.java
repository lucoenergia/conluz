package org.lucoenergia.conluz.infrastructure.shared.pagination;

import org.lucoenergia.conluz.domain.shared.pagination.Direction;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaginationMapper<T, R> {

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

    public PagedResult<R> mapResult(org.springframework.data.domain.Page<T> page, List<R> items) {
        return new PagedResult<>(items, page.getSize(), page.getTotalElements(), page.getTotalPages(),
                page.getNumber());
    }
}
