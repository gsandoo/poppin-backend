package com.poppin.poppinserver.core.dto;

import lombok.Builder;
import org.springframework.data.domain.Page;

@Builder
public record PageInfoDto(
        Integer page,
        Integer size,
        Integer totalPages,
        Boolean isLast
) {

    public static PageInfoDto from(int page, int size, int totalCount) {
        int totalPages = (int) Math.ceil((double) totalCount / size);
        boolean isLast = page >= (totalPages - 1);

        return PageInfoDto.builder()
                .page(page)
                .size(size)
                .totalPages(totalPages)
                .isLast(isLast)
                .build();
    }
    public static PageInfoDto fromPageInfo(Page<?> result) {

        return PageInfoDto.builder()
                .page(result.getPageable().getPageNumber())
                .size(result.getPageable().getPageSize())
                .totalPages(result.getTotalPages())
                .isLast(result.isLast())
                .build();
    }
}

