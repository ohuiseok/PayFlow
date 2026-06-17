package com.payflow.reward.dto;

import com.payflow.reward.entity.ParentChildLink;
import com.payflow.reward.entity.ParentChildLinkStatus;

public record FamilyLinkResponse(
        Long familyLinkId,
        Long parentUserId,
        Long childUserId,
        ParentChildLinkStatus status
) {

    public static FamilyLinkResponse from(ParentChildLink link) {
        return new FamilyLinkResponse(link.getId(), link.getParentUserId(), link.getChildUserId(), link.getStatus());
    }
}
