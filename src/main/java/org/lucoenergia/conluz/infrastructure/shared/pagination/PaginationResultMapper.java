package org.lucoenergia.conluz.infrastructure.shared.pagination;

import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaginationResultMapper<T, R> {

    public PagedResult<R> mapResult(org.springframework.data.domain.Page<T> page, List<R> items) {
        return new PagedResult<>(items, page.getSize(), page.getTotalElements(), page.getTotalPages(),
                page.getNumber());
    }
}
