package com.payflow.reward.dto;

import com.payflow.reward.entity.ParentChildLink;
import com.payflow.reward.entity.ParentChildLinkStatus;

public record FamilyLinkResponse(
        Long familyLinkId,
        Long parentUserId,
        Long childUserId,
        ParentChildLinkStatus status,
        String childName,
        String childPhoneNumber
) {

    public static FamilyLinkResponse from(ParentChildLink link) {
        return from(link, null, null);
    }

    public static FamilyLinkResponse from(ParentChildLink link, String childName, String childPhoneNumber) {
        return new FamilyLinkResponse(
                link.getId(),
                link.getParentUserId(),
                link.getChildUserId(),
                link.getStatus(),
                childName,
                childPhoneNumber
        );
    }
}
