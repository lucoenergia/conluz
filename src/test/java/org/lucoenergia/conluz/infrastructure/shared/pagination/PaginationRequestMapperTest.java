package org.lucoenergia.conluz.infrastructure.shared.pagination;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.shared.pagination.Direction;
import org.lucoenergia.conluz.domain.shared.pagination.Order;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

class PaginationRequestMapperTest {

    private final PaginationRequestMapper mapper = new PaginationRequestMapper();

    @Test
    void mapPageableToPagedRequest() {
        int pageSize = 11;
        int pageNumber = 1;

        // Page with custom size with default page number
        Pageable page = PageRequest.ofSize(pageSize);

        PagedRequest result = mapper.mapRequest(page);

        Assertions.assertEquals(pageSize, result.getSize());
        Assertions.assertEquals(0, result.getPage());
        Assertions.assertEquals(0, result.getOrders().size());

        // Page with custom size and page number
        page = PageRequest.of(pageNumber, pageSize);

        result = mapper.mapRequest(page);

        Assertions.assertEquals(pageSize, result.getSize());
        Assertions.assertEquals(1, result.getPage());
        Assertions.assertEquals(0, result.getOrders().size());

        // Page with custom size and page number and sorting by one field
        Sort sortFieldA = Sort.by(Sort.Direction.ASC, "aaa");
        page = PageRequest.of(pageNumber, pageSize, sortFieldA);

        result = mapper.mapRequest(page);

        Assertions.assertEquals(pageSize, result.getSize());
        Assertions.assertEquals(1, result.getPage());
        Assertions.assertEquals(1, result.getOrders().size());
        Assertions.assertEquals(Direction.ASC, result.getOrders().get(0).getDirection());
        Assertions.assertEquals("aaa", result.getOrders().get(0).getProperty());

        // Page with custom size and page number and sorting by more than one field
        page = PageRequest.of(pageNumber, pageSize, Sort.Direction.DESC, "aaa", "bbb");

        result = mapper.mapRequest(page);

        Assertions.assertEquals(pageSize, result.getSize());
        Assertions.assertEquals(1, result.getPage());
        Assertions.assertEquals(2, result.getOrders().size());
        Assertions.assertEquals(Direction.DESC, result.getOrders().get(0).getDirection());
        Assertions.assertEquals(Direction.DESC, result.getOrders().get(1).getDirection());
        Assertions.assertEquals("aaa", result.getOrders().get(0).getProperty());
        Assertions.assertEquals("bbb", result.getOrders().get(1).getProperty());
    }

    @Test
    void mapPagedRequestToPageable() {
        int pageSize = 11;
        int pageNumber = 1;

        // Page with custom size with default page number
        PagedRequest page = PagedRequest.of(pageNumber, pageSize);

        Pageable result = mapper.mapRequest(page);

        Assertions.assertEquals(pageSize, result.getPageSize());
        Assertions.assertEquals(pageNumber, result.getPageNumber());
        Assertions.assertFalse(result.getSort().isSorted());

        // Page with custom size and page number
        page = PagedRequest.of(pageNumber, pageSize,
                List.of(new Order(Direction.ASC, "aaa"), new Order(Direction.DESC, "bbb")));

        result = mapper.mapRequest(page);

        Assertions.assertEquals(pageSize, result.getPageSize());
        Assertions.assertEquals(1, result.getPageNumber());
        Assertions.assertTrue(result.getSort().isSorted());
        Assertions.assertEquals(2, result.getSort().stream().toList().size());
        Assertions.assertEquals("aaa", result.getSort().stream().toList().get(0).getProperty());
        Assertions.assertEquals("bbb", result.getSort().stream().toList().get(1).getProperty());
    }
}
