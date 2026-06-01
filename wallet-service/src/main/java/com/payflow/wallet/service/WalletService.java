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

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000");

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request, Long requestUserId) {
        if (!request.userId().equals(requestUserId)) {
            throw new BusinessException(ErrorCode.RESOURCE_OWNER_MISMATCH);
        }
        if (walletRepository.existsByUserId(request.userId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_WALLET);
        }

        try {
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

    @Transactional
    public WalletResponse deposit(Long walletId, WalletBalanceChangeRequest request, Long requestUserId, boolean internalRequest) {
        return changeBalance(walletId, request, WalletTransactionType.DEPOSIT, requestUserId, internalRequest);
    }

    @Transactional
    public WalletResponse withdraw(Long walletId, WalletBalanceChangeRequest request) {
        return changeBalance(walletId, request, WalletTransactionType.WITHDRAW, null, true);
    }

    private WalletResponse changeBalance(
            Long walletId,
            WalletBalanceChangeRequest request,
            WalletTransactionType transactionType,
            Long requestUserId,
            boolean internalRequest
    ) {
        BigDecimal amount = normalizeAmount(request.amount());
        String referenceType = request.referenceType().name();
        String referenceId = request.referenceId().trim();

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
        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));

        if (!internalRequest) {
            validateOwner(wallet, requestUserId);
        }

        var existingTransaction = walletTransactionRepository.findByWalletIdAndTransactionTypeAndReferenceTypeAndReferenceId(
                walletId,
                transactionType,
                referenceType,
                referenceId
        );
        if (existingTransaction.isPresent()) {
            return resolveDuplicateReference(existingTransaction.get(), amount, requestUserId, internalRequest);
        }

        BigDecimal balanceAfter = transactionType == WalletTransactionType.DEPOSIT
                ? wallet.deposit(amount)
                : wallet.withdraw(amount);

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
        if (amount == null
                || amount.scale() > 0
                || amount.compareTo(BigDecimal.ONE) < 0
                || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "금액은 1원 이상 10,000,000원 이하의 정수여야 합니다.");
        }
        return amount.setScale(0, RoundingMode.UNNECESSARY);
    }
}
