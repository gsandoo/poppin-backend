package com.poppin.poppinserver.popup.service;

import com.poppin.poppinserver.core.dto.PageInfoDto;
import com.poppin.poppinserver.core.dto.PagingResponseDto;
import com.poppin.poppinserver.core.exception.CommonException;
import com.poppin.poppinserver.core.exception.ErrorCode;
import com.poppin.poppinserver.core.type.EOperationStatus;
import com.poppin.poppinserver.core.type.EPopupSort;
import com.poppin.poppinserver.core.util.HeaderUtil;
import com.poppin.poppinserver.core.util.PrepardSearchUtil;

import com.poppin.poppinserver.popup.domain.Popup;
import com.poppin.poppinserver.popup.dto.popup.response.PopupStoreDto;
import com.poppin.poppinserver.popup.repository.PopupRepository;
import com.poppin.poppinserver.popup.usecase.BlockedPopupQueryUseCase;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchPopupService {
    private final PopupRepository popupRepository;

    private final PrepardSearchUtil prepardSearchUtil;

    private final PopupService popupService;
    private final PopupElasticsearchService popupElasticsearchService;
    private final BlockedPopupQueryUseCase blockedPopupQueryUseCase;
    private final HeaderUtil headerUtil;

    public PagingResponseDto<List<PopupStoreDto>> readSearchingList(
            String text,
            String filteringThreeCategories,
            String filteringFourteenCategories,
            EOperationStatus oper,
            EPopupSort order,
            int page,
            int size,
            HttpServletRequest request
    ) {
        Long userId = headerUtil.parseUserId(request);

        // 카테고리 파싱
        List<String> taste = Arrays.stream(filteringThreeCategories.split(",")).toList();
        List<String> preferred = Arrays.stream(filteringFourteenCategories.split(",")).toList();

        if (taste.isEmpty() || taste.get(0).isBlank()) {
            taste = List.of("market", "display", "experience");  // prefered 필드 기본값
        }
        if (preferred.isEmpty() || preferred.get(0).isBlank()) {
            preferred = List.of("fashionBeauty", "characters", "foodBeverage", "webtoonAnimation",
                    "interiorThings", "movie", "musical", "sports", "game", "itTech", "kpop", "alcohol", "animalPlant", "etc");  // taste 필드 기본값
        }

        // 유효성 검증
        validateInput(filteringThreeCategories, filteringFourteenCategories);

        // 블랙리스트 ID 조회
        List<Long> blockedIds = userId != null
                ? blockedPopupQueryUseCase.findBlockedPopupIds(userId)
                : List.of();

        // 필터값 준비 - 실제 ES 필드와 매핑 수정
        Map<String, Boolean> typeMap = Map.of(
                "market", taste.contains("market"),
                "display", taste.contains("display"),
                "experience", taste.contains("experience")
        );
        Map<String, Boolean> categoryMap = Map.ofEntries(
                Map.entry("fashionBeauty", preferred.contains("fashionBeauty")),
                Map.entry("characters", preferred.contains("characters")),
                Map.entry("foodBeverage", preferred.contains("foodBeverage")),
                Map.entry("webtoonAnimation", preferred.contains("webtoonAnimation")),
                Map.entry("interiorThings", preferred.contains("interiorThings")),
                Map.entry("movie", preferred.contains("movie")),
                Map.entry("musical", preferred.contains("musical")),
                Map.entry("sports", preferred.contains("sports")),
                Map.entry("game", preferred.contains("game")),
                Map.entry("itTech", preferred.contains("itTech")),
                Map.entry("kpop", preferred.contains("kpop")),
                Map.entry("alcohol", preferred.contains("alcohol")),
                Map.entry("animalPlant", preferred.contains("animalPlant")),
                Map.entry("etc", preferred.contains("etc"))
        );

        // 검색어 처리
        String searchText = (text != null && !text.trim().isEmpty()) ? prepardSearchUtil.prepareSearchText(text.trim()) : null;

        // Elasticsearch로 검색 - 파라미터 순서 수정
        List<Long> popupIds = popupElasticsearchService.search(
                text,
                searchText,
                categoryMap,  // 14개 카테고리 -> taste 필드
                typeMap,      // 3개 카테고리 -> prefered 필드
                oper.getStatus(),
                blockedIds,
                page,
                size,
                order
        );

        // DTO 변환
        List<PopupStoreDto> popupDtos;
        PageInfoDto pageInfoDto;

        if (userId != null) {
            // popupIds -> 팝업 엔티티 조회 (정렬 보존)
            Map<Long, Popup> popupMap = popupRepository.findByIdIn(popupIds).stream()
                    .collect(Collectors.toMap(Popup::getId, Function.identity()));

            List<Popup> orderedPopups = popupIds.stream()
                    .map(popupMap::get)
                    .filter(Objects::nonNull)
                    .toList();

            popupDtos = popupService.getPopupStoreDtos(orderedPopups, userId);
        } else {
            // 동일하게 비로그인용 처리
            Map<Long, Popup> popupMap = popupRepository.findByIdIn(popupIds).stream()
                    .collect(Collectors.toMap(Popup::getId, Function.identity()));

            List<Popup> orderedPopups = popupIds.stream()
                    .map(popupMap::get)
                    .filter(Objects::nonNull)
                    .toList();

            popupDtos = popupService.guestGetPopupStoreDtos(PageableExecutionUtils.getPage(
                    orderedPopups,
                    PageRequest.of(page, size),
                    () -> orderedPopups.size()
            ));
        }

        pageInfoDto = PageInfoDto.from(page, size, popupDtos.size());
        return PagingResponseDto.fromEntityAndPageInfo(popupDtos, pageInfoDto);
    }

    private void validateInput(String filteringThreeCategories, String filteringFourteenCategories) {
        // 허용된 카테고리 리스트
        List<String> validThreeCategories = List.of("market", "display", "experience");
        List<String> validFourteenCategories = List.of("fashionBeauty", "characters", "foodBeverage", "webtoonAnimation",
                "interiorThings", "movie", "musical", "sports", "game", "itTech", "kpop", "alcohol", "animalPlant", "etc");

        // filteringThreeCategories 유효성 검사
        if (filteringThreeCategories != null && !filteringThreeCategories.isEmpty()) {
            List<String> threeCategories = Arrays.stream(filteringThreeCategories.split(","))
                    .filter(category -> !category.isBlank()) // 빈 문자열 무시
                    .toList();

            for (String category : threeCategories) {
                if (!validThreeCategories.contains(category)) {
                    throw new CommonException(ErrorCode.INVALID_THREE_CATEGORY);
                }
            }
        }

        // filteringFourteenCategories 유효성 검사
        if (filteringFourteenCategories != null && !filteringFourteenCategories.isEmpty()) {
            List<String> fourteenCategories = Arrays.stream(filteringFourteenCategories.split(","))
                    .filter(category -> !category.isBlank()) // 빈 문자열 무시
                    .toList();

            for (String category : fourteenCategories) {
                if (!validFourteenCategories.contains(category)) {
                    throw new CommonException(ErrorCode.INVALID_FOURTEEN_CATEGORY);
                }
            }
        }
    }

}
