package com.poppin.poppinserver.popup.repository;

import com.poppin.poppinserver.popup.domain.PopupSearchDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface PopupSearchRepository extends ElasticsearchRepository<PopupSearchDocument, String> {
}
