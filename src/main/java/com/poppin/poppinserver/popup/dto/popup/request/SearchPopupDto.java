package com.poppin.poppinserver.popup.dto.popup.request;

import java.util.List;

public record SearchPopupDto(
        String keyword,
        List<String> taste,
        List<String> prefered,
        String operationStatus,
        String sort,
        int page,
        int size    
) {
    
}
