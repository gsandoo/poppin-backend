package com.poppin.poppinserver.popup.controller;

import com.poppin.poppinserver.popup.service.PopupIndexingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/es/index")
public class PopupIndexController {

    private final PopupIndexingService popupIndexingService;

    @PostMapping("/all")
    public ResponseEntity<String> indexAll() {
        popupIndexingService.indexAllPopups();
        return ResponseEntity.ok("Elasticsearch 색인 완료");
    }
}

