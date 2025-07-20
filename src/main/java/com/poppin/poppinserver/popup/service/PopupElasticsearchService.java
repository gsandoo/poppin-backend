package com.poppin.poppinserver.popup.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.poppin.poppinserver.popup.domain.Popup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PopupElasticsearchService {

    private final ElasticsearchClient esClient;

    public void save(Popup popup) {
        try {
            Map<String, Object> doc = Map.of(
                    "id", String.valueOf(popup.getId()),
                    "name", popup.getName(),
                    "introduce", popup.getIntroduce(),
                    "address", popup.getAddress(),
                    "openDate", popup.getOpenDate().toString(),
                    "closeDate", popup.getCloseDate().toString(),
                    "viewCnt", popup.getViewCnt(),
                    "createdAt", popup.getCreatedAt().toString(),
                    "editedAt", popup.getEditedAt().toString()
            );

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
