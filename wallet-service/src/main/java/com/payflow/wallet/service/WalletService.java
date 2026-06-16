package com.payflow.wallet.service;

import com.payflow.wallet.dto.CreateWalletRequest;
import com.payflow.wallet.dto.WalletBalanceChangeRequest;
import com.payflow.wallet.dto.WalletResponse;
import com.payflow.wallet.entity.Wallet;
import com.payflow.wallet.entity.WalletTransaction;
import com.payflow.wallet.entity.WalletTransactionType;
import com.payflow.wallet.repository.WalletRepository;
import com.payflow.wallet.repository.WalletTransactionRepository;
import com.payflow.wallet.support.error.BusinessException;
import com.payflow.wallet.support.error.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletService {

    // 결제/지갑 시스템에서는 금액 제한을 서비스 코드에서 한 번 더 두는 편이 안전하다.
    // 화면 검증이나 API DTO 검증이 빠져도 도메인 서비스가 최종 방어선 역할을 한다.
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000");

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request, Long requestUserId) {
        // 사용자는 자기 userId에 대해서만 지갑을 만들 수 있다.
        // 클라이언트가 body의 userId를 조작해 다른 사람 지갑을 만들지 못하게 막는다.
        if (!request.userId().equals(requestUserId)) {
            throw new BusinessException(ErrorCode.RESOURCE_OWNER_MISMATCH);
        }
        // 빠른 중복 확인이다. 실제 최종 보장은 wallets.user_id unique 제약이 담당한다.
        if (walletRepository.existsByUserId(request.userId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_WALLET);
        }

        try {
            // saveAndFlush로 DB unique 위반을 즉시 확인한다.
            // 동시 요청 두 개가 existsByUserId를 동시에 통과해도 둘 중 하나는 여기서 실패한다.
            return WalletResponse.from(walletRepository.saveAndFlush(new Wallet(request.userId())));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DUPLICATE_WALLET);
        }
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(Long walletId, Long requestUserId) {
        Wallet wallet = findWallet(walletId);
        validateOwner(wallet, requestUserId);
        return WalletResponse.from(wallet);
    }

    @Transactional(readOnly = true)
    public WalletResponse getWalletByUserId(Long userId) {
        return WalletResponse.from(walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND)));
    }

    @Transactional
    public WalletResponse deposit(Long walletId, WalletBalanceChangeRequest request, Long requestUserId, boolean internalRequest) {
        // 외부 사용자의 직접 입금과 내부 서비스의 입금 요청을 같은 로직으로 처리한다.
        // internalRequest가 false이면 소유자 검증을 하고, true이면 게이트웨이/서비스 간 secret 검증을 이미 통과했다고 본다.
        return changeBalance(walletId, request, WalletTransactionType.DEPOSIT, requestUserId, internalRequest);
    }

    @Transactional
    public WalletResponse withdraw(Long walletId, WalletBalanceChangeRequest request) {
        // 출금은 현재 내부 서비스 전용으로 사용된다.
        // 예: transfer-service가 송금 과정에서 보낸 사람 지갑에서 금액을 차감한다.
        return changeBalance(walletId, request, WalletTransactionType.WITHDRAW, null, true);
    }

    private WalletResponse changeBalance(
            Long walletId,
            WalletBalanceChangeRequest request,
            WalletTransactionType transactionType,
            Long requestUserId,
            boolean internalRequest
    ) {
        // 금액은 정수 원 단위만 허용한다. BigDecimal scale을 정리해 "1000"과 "1000.0" 같은 표현 차이를 제거한다.
        BigDecimal amount = normalizeAmount(request.amount());
        // referenceType + referenceId는 "이 잔액 변경이 어떤 업무 이벤트에서 왔는지"를 나타낸다.
        // 예: TRANSFER + 123 이면 123번 송금 때문에 발생한 지갑 반영이다.
        String referenceType = request.referenceType().name();
        String referenceId = request.referenceId().trim();

        // 같은 reference가 이미 있으면 잔액을 다시 변경하지 않고 기존 결과를 돌려준다.
        // 이것이 지갑 반영 단계의 멱등성(idempotency)이다.
        return walletTransactionRepository.findByWalletIdAndTransactionTypeAndReferenceTypeAndReferenceId(
                        walletId,
                        transactionType,
                        referenceType,
                        referenceId
                )
                .map(existing -> resolveDuplicateReference(existing, amount, requestUserId, internalRequest))
                .orElseGet(() -> createTransaction(walletId, transactionType, amount, referenceType, referenceId, requestUserId, internalRequest));
    }

    private WalletResponse createTransaction(
            Long walletId,
            WalletTransactionType transactionType,
            BigDecimal amount,
            String referenceType,
            String referenceId,
            Long requestUserId,
            boolean internalRequest
    ) {
        // 잔액 변경은 반드시 row lock을 잡고 처리한다.
        // 동시에 두 출금 요청이 들어왔을 때 둘 다 같은 초기 잔액을 보고 성공하는 lost update 문제를 막기 위해서다.
        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));

        // 내부 서비스 요청이 아닌 경우에는 JWT 사용자와 지갑 소유자가 같은지 확인한다.
        if (!internalRequest) {
            validateOwner(wallet, requestUserId);
        }

        // lock을 잡기 전 조회와 lock을 잡은 후 조회를 둘 다 한다.
        // 첫 조회는 빠른 반환용이고, 두 번째 조회는 동시 요청이 lock 대기 중 먼저 거래를 만든 경우를 다시 확인하기 위함이다.
        var existingTransaction = walletTransactionRepository.findByWalletIdAndTransactionTypeAndReferenceTypeAndReferenceId(
                walletId,
                transactionType,
                referenceType,
                referenceId
        );
        if (existingTransaction.isPresent()) {
            return resolveDuplicateReference(existingTransaction.get(), amount, requestUserId, internalRequest);
        }

        // 실제 잔액 계산은 Wallet 엔티티에 맡긴다.
        // 서비스는 흐름을 조율하고, 잔액이 음수가 되면 안 된다는 규칙은 도메인 객체가 지킨다.
        BigDecimal balanceAfter = transactionType == WalletTransactionType.DEPOSIT
                ? wallet.deposit(amount)
                : wallet.withdraw(amount);

        // 지갑 잔액만 바꾸면 나중에 "왜 잔액이 바뀌었는지"를 알 수 없다.
        // 그래서 변경 후 잔액(balanceAfter)까지 거래 내역에 남겨 감사 추적이 가능하게 한다.
        WalletTransaction transaction = new WalletTransaction(
                wallet,
                transactionType,
                amount,
                balanceAfter,
                referenceType,
                referenceId
        );

        walletTransactionRepository.saveAndFlush(transaction);
        return WalletResponse.from(wallet);
    }

    private WalletResponse resolveDuplicateReference(
            WalletTransaction transaction,
            BigDecimal amount,
            Long requestUserId,
            boolean internalRequest
    ) {
        // 같은 reference가 같은 금액으로 다시 오면 네트워크 재시도라고 보고 기존 결과를 반환한다.
        // 같은 reference인데 금액이 다르면 같은 멱등키를 다른 요청에 재사용한 것이므로 충돌로 처리한다.
        if (!internalRequest) {
            validateOwner(transaction.getWallet(), requestUserId);
        }
        if (transaction.getAmount().compareTo(amount) != 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_WALLET_REFERENCE);
        }
        return new WalletResponse(
                transaction.getWallet().getId(),
                transaction.getWallet().getUserId(),
                transaction.getBalanceAfter(),
                transaction.getWallet().getStatus()
        );
    }

    private Wallet findWallet(Long walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
    }

    private void validateOwner(Wallet wallet, Long requestUserId) {
        if (!wallet.getUserId().equals(requestUserId)) {
            throw new BusinessException(ErrorCode.RESOURCE_OWNER_MISMATCH);
        }
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        // scale > 0은 소수점이 있다는 뜻이다. 현재 시스템은 원 단위 정수만 다루므로 소수 금액을 거부한다.
        // BigDecimal은 double보다 금액 계산에 적합하지만, 표현 차이를 줄이기 위해 scale 검증이 필요하다.
        if (amount == null
                || amount.scale() > 0
                || amount.compareTo(BigDecimal.ONE) < 0
                || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "금액은 1원 이상 10,000,000원 이하의 정수여야 합니다.");
        }
        return amount.setScale(0, RoundingMode.UNNECESSARY);
    }
}
