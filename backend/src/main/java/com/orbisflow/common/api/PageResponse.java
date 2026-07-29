package com.orbisflow.common.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        @JsonProperty("total_elements") long totalElements,
        @JsonProperty("total_pages") int totalPages,
        SortView sort
) {
    public static <T> PageResponse<T> of(
            List<T> items,
            int page,
            int size,
            long totalElements,
            String sortField,
            String direction) {
        int pages = totalElements == 0
                ? 0
                : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(
                List.copyOf(items),
                page,
                size,
                totalElements,
                pages,
                new SortView(sortField, direction));
    }

    public record SortView(String field, String direction) {
    }
}
