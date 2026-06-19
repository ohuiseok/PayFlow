package com.payflow.reward.repository;

import com.payflow.reward.entity.RewardTask;
import com.payflow.reward.entity.RewardTaskStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardTaskRepository extends JpaRepository<RewardTask, Long> {

    List<RewardTask> findByParentUserIdOrderByCreatedAtDesc(Long parentUserId);

    List<RewardTask> findByChildUserIdOrderByCreatedAtDesc(Long childUserId);

    List<RewardTask> findByParentUserIdAndStatusInOrderByCreatedAtDesc(Long parentUserId, Collection<RewardTaskStatus> statuses);

    List<RewardTask> findByChildUserIdAndStatusInOrderByCreatedAtDesc(Long childUserId, Collection<RewardTaskStatus> statuses);
}
