package com.payflow.reward.service;

import com.payflow.reward.client.CreateTransferRequest;
import com.payflow.reward.client.TransferClient;
import com.payflow.reward.client.TransferResponse;
import com.payflow.reward.client.WalletClient;
import com.payflow.reward.dto.CashbookSummaryResponse;
import com.payflow.reward.dto.CreateFamilyLinkRequest;
import com.payflow.reward.dto.CreateMissionRequest;
import com.payflow.reward.dto.FamilyLinkResponse;
import com.payflow.reward.dto.MissionResponse;
import com.payflow.reward.dto.ParentCreditSummaryResponse;
import com.payflow.reward.dto.RejectMissionRequest;
import com.payflow.reward.dto.SubmitMissionRequest;
import com.payflow.reward.entity.ParentChildLink;
import com.payflow.reward.entity.ParentChildLinkStatus;
import com.payflow.reward.entity.RewardTask;
import com.payflow.reward.entity.RewardTaskStatus;
import com.payflow.reward.repository.ParentChildLinkRepository;
import com.payflow.reward.repository.RewardTaskRepository;
import com.payflow.reward.support.error.BusinessException;
import com.payflow.reward.support.error.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RewardService {

    private static final String ROLE_PARENT = "PARENT";
    private static final String ROLE_CHILD = "CHILD";
    private static final BigDecimal MAX_REWARD_AMOUNT = new BigDecimal("10000000");

    private final ParentChildLinkRepository parentChildLinkRepository;
    private final RewardTaskRepository rewardTaskRepository;
    private final TransferClient transferClient;
    private final WalletClient walletClient;

    @Value("${internal.secret:}")
    private String internalSecret;

    @Transactional
    public FamilyLinkResponse createFamilyLink(CreateFamilyLinkRequest request, Long requestUserId, String role) {
        requireRole(role, ROLE_PARENT);
        if (requestUserId.equals(request.childUserId())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "parent and child must be different");
        }

        ParentChildLink link = parentChildLinkRepository.findByParentUserIdAndChildUserId(requestUserId, request.childUserId())
                .map(existing -> {
                    if (existing.getStatus() == ParentChildLinkStatus.ACTIVE) {
                        throw new BusinessException(ErrorCode.DUPLICATE_FAMILY_LINK);
                    }
                    existing.activate();
                    return existing;
                })
                .orElseGet(() -> parentChildLinkRepository.save(new ParentChildLink(requestUserId, request.childUserId())));
        return FamilyLinkResponse.from(link);
    }

    @Transactional(readOnly = true)
    public List<FamilyLinkResponse> getChildren(Long requestUserId, String role) {
        requireRole(role, ROLE_PARENT);
        return parentChildLinkRepository.findByParentUserIdAndStatusOrderByIdDesc(requestUserId, ParentChildLinkStatus.ACTIVE)
                .stream()
                .map(FamilyLinkResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FamilyLinkResponse> getParents(Long requestUserId, String role) {
        requireRole(role, ROLE_CHILD);
        return parentChildLinkRepository.findByChildUserIdAndStatusOrderByIdDesc(requestUserId, ParentChildLinkStatus.ACTIVE)
                .stream()
                .map(FamilyLinkResponse::from)
                .toList();
    }

    @Transactional
    public MissionResponse createMission(CreateMissionRequest request, Long requestUserId, String role) {
        requireRole(role, ROLE_PARENT);
        requireActiveFamilyLink(requestUserId, request.childUserId());

        RewardTask task = rewardTaskRepository.save(new RewardTask(
                requestUserId,
                request.childUserId(),
                request.title().trim(),
                request.description().trim(),
                normalizeAmount(request.rewardAmount())
        ));
        return MissionResponse.from(task);
    }

    @Transactional(readOnly = true)
    public List<MissionResponse> getMissions(Long requestUserId, String role, RewardTaskStatus status) {
        List<RewardTask> tasks;
        if (ROLE_PARENT.equals(role)) {
            tasks = rewardTaskRepository.findByParentUserIdOrderByCreatedAtDesc(requestUserId);
        } else if (ROLE_CHILD.equals(role)) {
            tasks = rewardTaskRepository.findByChildUserIdOrderByCreatedAtDesc(requestUserId);
        } else {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        return tasks.stream()
                .filter(task -> status == null || task.getStatus() == status)
                .map(MissionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MissionResponse getMission(Long missionId, Long requestUserId, String role) {
        RewardTask task = findMission(missionId);
        validateMissionAccess(task, requestUserId, role);
        return MissionResponse.from(task);
    }

    @Transactional
    public MissionResponse submitMission(Long missionId, SubmitMissionRequest request, Long requestUserId, String role) {
        requireRole(role, ROLE_CHILD);
        RewardTask task = findMission(missionId);
        requireChild(task, requestUserId);
        if (task.getStatus() != RewardTaskStatus.CREATED && task.getStatus() != RewardTaskStatus.REJECTED) {
            throw new BusinessException(ErrorCode.INVALID_MISSION_STATUS);
        }
        task.submit(StringUtils.hasText(request.submissionNote()) ? request.submissionNote().trim() : null);
        return MissionResponse.from(task);
    }

    @Transactional
    public MissionResponse approveMission(Long missionId, Long requestUserId, String role) {
        requireRole(role, ROLE_PARENT);
        RewardTask task = findMission(missionId);
        requireParent(task, requestUserId);
        if (task.getStatus() != RewardTaskStatus.SUBMITTED) {
            throw new BusinessException(ErrorCode.INVALID_MISSION_STATUS);
        }
        task.approve();
        return MissionResponse.from(task);
    }

    @Transactional
    public MissionResponse rejectMission(Long missionId, RejectMissionRequest request, Long requestUserId, String role) {
        requireRole(role, ROLE_PARENT);
        RewardTask task = findMission(missionId);
        requireParent(task, requestUserId);
        if (task.getStatus() != RewardTaskStatus.SUBMITTED) {
            throw new BusinessException(ErrorCode.INVALID_MISSION_STATUS);
        }
        task.reject(request.reason().trim());
        return MissionResponse.from(task);
    }

    @Transactional
    public MissionResponse payMission(Long missionId, Long requestUserId, String role) {
        requireRole(role, ROLE_PARENT);
        // [C-6] 비관적 락을 획득해 동시 지급 요청에 의한 중복 보상을 방지한다.
        // 같은 missionId로 두 요청이 동시에 들어오면 하나만 락을 잡고, 나머지는 대기 후 PAID 상태를 보게 된다.
        RewardTask task = rewardTaskRepository.findByIdForUpdate(missionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MISSION_NOT_FOUND));
        requireParent(task, requestUserId);
        if (task.getStatus() == RewardTaskStatus.PAID) {
            return MissionResponse.from(task);
        }
        if (task.getStatus() != RewardTaskStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_MISSION_STATUS);
        }

        try {
            TransferResponse response = transferClient.createTransfer(
                    new CreateTransferRequest(task.getChildUserId(), task.getRewardAmount()),
                    "reward-payment-" + task.getId(),
                    task.getParentUserId(),
                    internalSecret
            );
            if (!"SUCCEEDED".equals(response.status())) {
                String reason = StringUtils.hasText(response.failureReason()) ? response.failureReason() : "transfer status: " + response.status();
                task.recordPaymentFailure(reason);
                throw new BusinessException(ErrorCode.REWARD_PAYMENT_FAILED, reason);
            }
            task.markPaid(response.transferId());
            return MissionResponse.from(task);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            String reason = resolveMessage(exception);
            task.recordPaymentFailure(reason);
            throw new BusinessException(ErrorCode.REWARD_PAYMENT_FAILED, reason);
        }
    }

    @Transactional(readOnly = true)
    public CashbookSummaryResponse getCashbookSummary(Long childUserId, Long requestUserId, String role) {
        validateCashbookAccess(childUserId, requestUserId, role);
        BigDecimal balance = walletClient.getWalletByUserId(childUserId, true, internalSecret).balance();
        List<RewardTask> paidTasks = rewardTaskRepository.findByChildUserIdAndStatusInOrderByCreatedAtDesc(
                childUserId,
                List.of(RewardTaskStatus.PAID)
        );
        BigDecimal paidAmount = paidTasks.stream()
                .map(RewardTask::getRewardAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CashbookSummaryResponse(childUserId, balance, paidAmount, paidTasks.size());
    }

    @Transactional(readOnly = true)
    public ParentCreditSummaryResponse getParentCreditSummary(Long requestUserId, String role) {
        requireRole(role, ROLE_PARENT);
        var wallet = walletClient.getWalletByUserId(requestUserId, true, internalSecret);

        // [M-4] 전체 목록을 메모리에 로드해 스트림 필터링하는 대신 DB에서 COUNT/SUM으로 집계한다.
        // 미션 수가 늘어도 DB가 인덱스를 활용해 빠르게 처리한다.
        YearMonth currentMonth = YearMonth.now();
        LocalDateTime monthStart = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = currentMonth.plusMonths(1).atDay(1).atStartOfDay();

        BigDecimal monthlyRewardPaid = rewardTaskRepository.sumRewardAmountByParentAndStatusAndPeriod(
                requestUserId,
                RewardTaskStatus.PAID,
                monthStart,
                monthEnd
        );
        long pendingApprovalCount = rewardTaskRepository.countByParentUserIdAndStatus(
                requestUserId,
                RewardTaskStatus.SUBMITTED
        );
        return new ParentCreditSummaryResponse(wallet.walletId(), wallet.balance(), monthlyRewardPaid, pendingApprovalCount);
    }

    @Transactional(readOnly = true)
    public List<MissionResponse> getCashbookEntries(Long childUserId, Long requestUserId, String role) {
        validateCashbookAccess(childUserId, requestUserId, role);
        return rewardTaskRepository.findByChildUserIdAndStatusInOrderByCreatedAtDesc(childUserId, List.of(RewardTaskStatus.PAID))
                .stream()
                .map(MissionResponse::from)
                .toList();
    }

    private RewardTask findMission(Long missionId) {
        return rewardTaskRepository.findById(missionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MISSION_NOT_FOUND));
    }

    private void requireActiveFamilyLink(Long parentUserId, Long childUserId) {
        if (!parentChildLinkRepository.existsByParentUserIdAndChildUserIdAndStatus(parentUserId, childUserId, ParentChildLinkStatus.ACTIVE)) {
            throw new BusinessException(ErrorCode.FAMILY_LINK_NOT_FOUND);
        }
    }

    private void validateMissionAccess(RewardTask task, Long requestUserId, String role) {
        if (ROLE_PARENT.equals(role)) {
            requireParent(task, requestUserId);
            return;
        }
        if (ROLE_CHILD.equals(role)) {
            requireChild(task, requestUserId);
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private void validateCashbookAccess(Long childUserId, Long requestUserId, String role) {
        if (ROLE_CHILD.equals(role) && childUserId.equals(requestUserId)) {
            return;
        }
        if (ROLE_PARENT.equals(role)) {
            requireActiveFamilyLink(requestUserId, childUserId);
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private void requireParent(RewardTask task, Long requestUserId) {
        if (!task.getParentUserId().equals(requestUserId)) {
            throw new BusinessException(ErrorCode.RESOURCE_OWNER_MISMATCH);
        }
    }

    private void requireChild(RewardTask task, Long requestUserId) {
        if (!task.getChildUserId().equals(requestUserId)) {
            throw new BusinessException(ErrorCode.RESOURCE_OWNER_MISMATCH);
        }
    }

    private void requireRole(String actualRole, String requiredRole) {
        if (!requiredRole.equals(actualRole)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null
                || amount.scale() > 0
                || amount.compareTo(BigDecimal.ONE) < 0
                || amount.compareTo(MAX_REWARD_AMOUNT) > 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "rewardAmount must be an integer from 1 to 10000000");
        }
        return amount.setScale(0, RoundingMode.UNNECESSARY);
    }

    private String resolveMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
