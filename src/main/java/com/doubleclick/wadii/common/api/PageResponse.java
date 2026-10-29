package com.doubleclick.wadii.common.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.function.Function;

/** Paged list payload. Every list endpoint returns this; there are no unbounded lists. */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /** Clamps client paging input: page >= 0, 1 <= size <= {@link #MAX_SIZE}. */
    public static Pageable pageable(Integer page, Integer size, Sort sort) {
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return PageRequest.of(p, s, sort);
    }

    public static Pageable pageable(Integer page, Integer size) {
        return pageable(page, size, Sort.unsorted());
    }
}
