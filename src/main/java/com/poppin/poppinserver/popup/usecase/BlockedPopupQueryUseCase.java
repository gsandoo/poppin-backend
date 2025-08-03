package com.poppin.poppinserver.popup.usecase;

import com.poppin.poppinserver.core.annotation.UseCase;

import java.util.List;

@UseCase
public interface BlockedPopupQueryUseCase {
    Boolean existBlockedPopupByUserIdAndPopupId(Long userId, Long popupId);
    List<Long> findBlockedPopupIds(Long userId);
}
