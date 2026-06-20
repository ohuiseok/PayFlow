package com.payflow.reward.repository;

import com.payflow.reward.entity.RewardTask;
import com.payflow.reward.entity.RewardTaskStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RewardTaskRepository extends JpaRepository<RewardTask, Long> {

    List<RewardTask> findByParentUserIdOrderByCreatedAtDesc(Long parentUserId);

    List<RewardTask> findByChildUserIdOrderByCreatedAtDesc(Long childUserId);

    List<RewardTask> findByParentUserIdAndStatusInOrderByCreatedAtDesc(Long parentUserId, Collection<RewardTaskStatus> statuses);

    List<RewardTask> findByChildUserIdAndStatusInOrderByCreatedAtDesc(Long childUserId, Collection<RewardTaskStatus> statuses);

    // [C-6] 보상 지급 시 비관적 락을 획득해 동일 미션에 대한 중복 지급을 방지한다.
    // 동시에 두 요청이 payMission을 호출해도 하나만 실제 지급을 실행하고 나머지는 이미 PAID 상태를 보게 된다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RewardTask t WHERE t.id = :id")
    Optional<RewardTask> findByIdForUpdate(@Param("id") Long id);

    // [M-4] DB COUNT 쿼리로 대체: 전체 목록 로드 후 size() 대신 COUNT만 조회한다.
    long countByParentUserIdAndStatus(Long parentUserId, RewardTaskStatus status);

    // [M-4] DB SUM 쿼리로 대체: 전체 목록 메모리 필터링 대신 DB에서 직접 합산한다.
    @Query("SELECT COALESCE(SUM(t.rewardAmount), 0) FROM RewardTask t WHERE t.parentUserId = :parentUserId AND t.status = :status AND t.updatedAt >= :from AND t.updatedAt < :to")
    BigDecimal sumRewardAmountByParentAndStatusAndPeriod(
            @Param("parentUserId") Long parentUserId,
            @Param("status") RewardTaskStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
