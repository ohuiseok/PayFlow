package com.payflow.banking.service;

import com.payflow.banking.client.WalletBalanceChangeRequest;
import com.payflow.banking.client.WalletClient;
import com.payflow.banking.dto.BankAccountResponse;
import com.payflow.banking.dto.BankingTransferResponse;
import com.payflow.banking.dto.CreateBankAccountRequest;
import com.payflow.banking.dto.CreateDepositRequest;
import com.payflow.banking.entity.BankAccount;
import com.payflow.banking.entity.BankAccountStatus;
import com.payflow.banking.entity.BankingTransfer;
import com.payflow.banking.repository.BankAccountRepository;
import com.payflow.banking.repository.BankingTransferRepository;
import com.payflow.banking.support.error.BusinessException;
import com.payflow.banking.support.error.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class BankingService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000");
    private static final String DEPOSIT_REFERENCE_TYPE = "MANUAL_CHARGE";

    private final BankAccountRepository bankAccountRepository;
    private final BankingTransferRepository bankingTransferRepository;
    private final WalletClient walletClient;

    @Value("${internal.secret:}")
    private String internalSecret;

    @Transactional
    public BankAccountResponse createBankAccount(CreateBankAccountRequest request, Long requestUserId) {
        String bankCode = request.bankCode().trim();
        String accountNumber = normalizeAccountNumber(request.accountNumber());
        String holderName = request.accountHolderName().trim();

        if (bankAccountRepository.existsByUserIdAndBankCodeAndAccountNumber(requestUserId, bankCode, accountNumber)) {
            throw new BusinessException(ErrorCode.DUPLICATE_BANK_ACCOUNT);
        }

        BankAccount account = bankAccountRepository.save(new BankAccount(
                requestUserId,
                bankCode,
                accountNumber,
                maskAccountNumber(accountNumber),
                holderName
        ));
        return BankAccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public List<BankAccountResponse> getBankAccounts(Long requestUserId) {
        return bankAccountRepository.findByUserIdAndStatusOrderByIdDesc(requestUserId, BankAccountStatus.ACTIVE)
                .stream()
                .map(BankAccountResponse::from)
                .toList();
    }

    @Transactional
    public BankingTransferResponse createDeposit(CreateDepositRequest request, String idempotencyKey, Long requestUserId) {
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        BigDecimal amount = normalizeAmount(request.amount());
        BankAccount account = bankAccountRepository.findByIdAndUserIdAndStatus(request.bankAccountId(), requestUserId, BankAccountStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.BANK_ACCOUNT_NOT_FOUND));

        String requestHash = createRequestHash(requestUserId, account.getId(), amount);
        return bankingTransferRepository.findByIdempotencyKey(normalizedIdempotencyKey)
                .map(existing -> resolveExistingTransfer(existing, requestHash))
                .orElseGet(() -> processNewDeposit(requestUserId, account.getId(), amount, normalizedIdempotencyKey, requestHash));
    }

    @Transactional(readOnly = true)
    public BankingTransferResponse getTransfer(Long bankingTransferId, Long requestUserId) {
        return bankingTransferRepository.findByIdAndUserId(bankingTransferId, requestUserId)
                .map(BankingTransferResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.BANKING_TRANSFER_NOT_FOUND));
    }

    private BankingTransferResponse resolveExistingTransfer(BankingTransfer transfer, String requestHash) {
        if (!transfer.getRequestHash().equals(requestHash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_REQUEST_MISMATCH);
        }
        return BankingTransferResponse.from(transfer);
    }

    private BankingTransferResponse processNewDeposit(
            Long userId,
            Long bankAccountId,
            BigDecimal amount,
            String idempotencyKey,
            String requestHash
    ) {
        BankingTransfer transfer = bankingTransferRepository.saveAndFlush(new BankingTransfer(
                userId,
                bankAccountId,
                amount,
                idempotencyKey,
                requestHash,
                createBankTranId(userId, idempotencyKey)
        ));

        try {
            var wallet = walletClient.getWalletByUserId(userId, true, internalSecret);
            walletClient.deposit(
                    wallet.walletId(),
                    new WalletBalanceChangeRequest(amount, DEPOSIT_REFERENCE_TYPE, transfer.getId().toString()),
                    true,
                    internalSecret
            );
            transfer.succeed(wallet.walletId(), null);
            return BankingTransferResponse.from(transfer);
        } catch (RuntimeException exception) {
            transfer.fail(resolveMessage(exception));
            return BankingTransferResponse.from(transfer);
        }
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        return idempotencyKey.trim();
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null
                || amount.scale() > 0
                || amount.compareTo(BigDecimal.ONE) < 0
                || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "amount must be an integer from 1 to 10000000");
        }
        return amount.setScale(0, RoundingMode.UNNECESSARY);
    }

    private String normalizeAccountNumber(String accountNumber) {
        String normalized = accountNumber == null ? "" : accountNumber.replaceAll("[^0-9]", "");
        if (normalized.length() < 6 || normalized.length() > 30) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "accountNumber must contain 6 to 30 digits");
        }
        return normalized;
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber.length() <= 7) {
            return accountNumber.charAt(0) + "****" + accountNumber.substring(accountNumber.length() - 2);
        }
        return accountNumber.substring(0, 3) + "-****-" + accountNumber.substring(accountNumber.length() - 4);
    }

    private String createRequestHash(Long userId, Long bankAccountId, BigDecimal amount) {
        return sha256(userId + ":" + bankAccountId + ":" + amount.toPlainString());
    }

    private String createBankTranId(Long userId, String idempotencyKey) {
        return "MOCK-" + userId + "-" + sha256(idempotencyKey).substring(0, 24);
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String resolveMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
