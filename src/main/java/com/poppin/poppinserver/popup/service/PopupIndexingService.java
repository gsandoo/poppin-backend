package com.poppin.poppinserver.popup.service;

import com.poppin.poppinserver.core.type.EOperationStatus;
import com.poppin.poppinserver.popup.domain.Popup;
import com.poppin.poppinserver.popup.domain.PopupSearchDocument;
import com.poppin.poppinserver.popup.repository.PopupRepository;
import com.poppin.poppinserver.popup.repository.PopupSearchRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PopupIndexingService {

    private final PopupRepository popupRepository;
    private final PopupSearchRepository popupSearchRepository;
    private final PopupElasticsearchService popupElasticsearchService;

    /**
     * 전체 팝업 데이터를 Elasticsearch에 저장
     */
    public void indexAllPopups() {
        List<Popup> allPopups = popupRepository.findAll();

        for (Popup popup : allPopups) {
            popupElasticsearchService.save(popup);
        }
    }

    @PostConstruct
    public void initIndexing() {
        reindexAllPopupData();
    }

    public void reindexAllPopupData() {
        List<Popup> popups = popupRepository.findAll();

        List<PopupSearchDocument> docs = popups.stream()
                .map(p -> PopupSearchDocument.builder()
                        .id(String.valueOf(p.getId()))
                        .name(p.getName())
                        .introduce(p.getIntroduce())
                        .address(p.getAddress())
                        .operationStatus(EOperationStatus.valueOf(p.getOperationStatus()))
                        .prefered(p.getPreferedPopup().getSelectedKeys()) // enum → string
                        .taste(p.getTastePopup().getSelectedKeys())
                        .openDate(p.getOpenDate())
                        .closeDate(p.getCloseDate())
                        .createdAt(p.getCreatedAt())
                        .editedAt(p.getEditedAt())
                        .viewCnt(p.getViewCnt())
                        .build())
                .toList();

        popupSearchRepository.saveAll(docs);
    }
}
