package com.ncba.countryinfo.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/** One page of results plus the paging metadata clients need to fetch the next one. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }
}
