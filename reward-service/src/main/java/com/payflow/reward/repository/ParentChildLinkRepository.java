package com.payflow.reward.repository;

import com.payflow.reward.entity.ParentChildLink;
import com.payflow.reward.entity.ParentChildLinkStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParentChildLinkRepository extends JpaRepository<ParentChildLink, Long> {

    Optional<ParentChildLink> findByParentUserIdAndChildUserId(Long parentUserId, Long childUserId);

    boolean existsByParentUserIdAndChildUserIdAndStatus(Long parentUserId, Long childUserId, ParentChildLinkStatus status);

    List<ParentChildLink> findByParentUserIdAndStatusOrderByIdDesc(Long parentUserId, ParentChildLinkStatus status);
}
