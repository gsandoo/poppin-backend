package com.poppin.poppinserver.popup.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.poppin.poppinserver.core.type.EPopupSort;
import com.poppin.poppinserver.popup.domain.Popup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class PopupElasticsearchService {

    private final ElasticsearchClient esClient;

    public List<Long> search(
            String rawText,
            String preparedText,
            Map<String, Boolean> tasteMap,
            Map<String, Boolean> categoryMap,
            String operationStatus,
            List<Long> blockedIds,
            int page,
            int size,
            EPopupSort order
    ) {
        String sortField = switch (order) {
            case RECENTLY_OPENED -> "openDate";
            case CLOSING_SOON -> "closeDate";
            case MOST_VIEWED -> "viewCnt";
            case RECENTLY_UPLOADED -> "createdAt";
            default -> "createdAt";
        };

        try {
            SearchResponse<Map> response = esClient.search(s -> s
                    .index("popup-store")
                    .from(page * size)
                    .size(size)
                    .query(q -> q.bool(b -> {

                        // 검색어 처리
                        if (rawText != null && !rawText.isBlank()) {

                            if (rawText.length() <= 5) {
                                b.must(m -> m.queryString(qs -> qs
                                        .fields("name", "introduce", "address")
                                        .query("*" + rawText + "*")
                                ));
                            } else {
                                b.must(m -> m.multiMatch(mm -> mm
                                        .query(rawText)
                                        .fields("name", "introduce", "address")
                                        .operator(Operator.Or)
                                ));
                            }
                        }
                        // taste 필터 (14개 카테고리 -> taste 필드)
                        boolean hasTasteFilter = tasteMap != null && !tasteMap.isEmpty() &&
                                tasteMap.values().stream().anyMatch(Boolean::valueOf);

                        if (hasTasteFilter) {
                            List<String> activeTastes = tasteMap.entrySet().stream()
                                    .filter(Map.Entry::getValue)
                                    .map(Map.Entry::getKey)
                                    .toList();

                            List<FieldValue> tasteValues = activeTastes.stream()
                                    .map(FieldValue::of)
                                    .toList();

                            b.filter(f -> f.terms(t -> t
                                    .field("taste")  // 실제 데이터의 taste 필드
                                    .terms(v -> v.value(tasteValues))
                            ));
                        }

                        // preferred 필터 (3개 카테고리 -> prefered 필드)
                        boolean hasCategoryFilter = categoryMap != null && !categoryMap.isEmpty() &&
                                categoryMap.values().stream().anyMatch(Boolean::valueOf);

                        if (hasCategoryFilter) {
                            List<String> activeCategories = categoryMap.entrySet().stream()
                                    .filter(Map.Entry::getValue)
                                    .map(Map.Entry::getKey)
                                    .toList();

                            List<FieldValue> categoryValues = activeCategories.stream()
                                    .map(FieldValue::of)
                                    .toList();

                            b.filter(f -> f.terms(t -> t
                                    .field("prefered")  // 실제 데이터의 prefered 필드
                                    .terms(v -> v.value(categoryValues))
                            ));
                        }

                        // operationStatus 필터
                        if (operationStatus != null && !operationStatus.isBlank()) {
                            b.filter(f -> f.term(t -> t
                                    .field("operationStatus")
                                    .value(operationStatus)
                            ));
                        }

                        // 블랙리스트 제외
                        if (blockedIds != null && !blockedIds.isEmpty()) {
                            b.mustNot(mn -> mn.ids(i -> i.values(
                                    blockedIds.stream().map(String::valueOf).toList()
                            )));
                        }

                        return b;
                    }))
                    .sort(srt -> srt.field(f -> f
                            .field(sortField)
                            .order(order == EPopupSort.CLOSING_SOON ? SortOrder.Asc : SortOrder.Desc)
                    )), Map.class);

            if (tasteMap != null) {
                List<String> activeTastes = tasteMap.entrySet().stream()
                        .filter(Map.Entry::getValue)
                        .map(Map.Entry::getKey)
                        .toList();
                System.out.println("Active tastes (-> taste field): " + activeTastes);
            }

            if (categoryMap != null) {
                List<String> activeCategories = categoryMap.entrySet().stream()
                        .filter(Map.Entry::getValue)
                        .map(Map.Entry::getKey)
                        .toList();
                System.out.println("Active categories (-> prefered field): " + activeCategories);
            }

            return response.hits().hits().stream()
                    .map(Hit::id)
                    .map(Long::valueOf)
                    .toList();

        } catch (IOException e) {
            System.err.println("Elasticsearch search error: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Elasticsearch 검색 오류", e);
        }
    }
    public void save(Popup popup) {
        try {
            List<String> preferedList = popup.getPreferedPopup().getSelectedKeys();
            List<String> tasteList = popup.getTastePopup().getSelectedKeys();

            Map<String, Object> doc = new HashMap<>();
            doc.put("id", String.valueOf(popup.getId()));
            doc.put("name", popup.getName());
            doc.put("introduce", popup.getIntroduce());
            doc.put("address", popup.getAddress());
            doc.put("openDate", popup.getOpenDate().toString());
            doc.put("closeDate", popup.getCloseDate().toString());
            doc.put("viewCnt", popup.getViewCnt());
            doc.put("createdAt", popup.getCreatedAt().toString());
            doc.put("editedAt", popup.getEditedAt().toString());
            doc.put("operationStatus", popup.getOperationStatus());
            doc.put("prefered", preferedList);
            doc.put("taste", tasteList);

            esClient.index(i -> i
                    .index("popup-store")
                    .id(String.valueOf(popup.getId()))
                    .document(doc)
            );

        } catch (IOException e) {
            throw new RuntimeException("Elasticsearch 색인 실패", e);
        }
    }

    public void delete(Long popupId) {
        try {
            esClient.delete(d -> d
                    .index("popup-store")
                    .id(String.valueOf(popupId))
            );
        } catch (IOException e) {
            throw new RuntimeException("Elasticsearch 삭제 실패", e);
        }
    }
}
