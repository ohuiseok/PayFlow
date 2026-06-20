package com.payflow.banking.service;

import com.payflow.banking.client.WalletBalanceChangeRequest;
import com.payflow.banking.client.WalletClient;
import com.payflow.banking.dto.BankAccountResponse;
import com.payflow.banking.dto.BankingTransferResponse;
import com.payflow.banking.dto.CreateBankAccountRequest;
import com.payflow.banking.dto.CreateDepositRequest;
import com.payflow.banking.dto.CreateWithdrawalRequest;
import com.payflow.banking.dto.OpenBankingAttemptResponse;
import com.payflow.banking.dto.OpenBankingAuthorizeUrlResponse;
import com.payflow.banking.dto.OpenBankingCallbackRequest;
import com.payflow.banking.dto.OpenBankingCallbackResponse;
import com.payflow.banking.entity.BankingApiLog;
import com.payflow.banking.entity.BankAccount;
import com.payflow.banking.entity.BankAccountStatus;
import com.payflow.banking.entity.BankingTransfer;
import com.payflow.banking.entity.BankingTransferStatus;
import com.payflow.banking.entity.OpenBankingToken;
import com.payflow.banking.entity.OpenBankingTokenType;
import com.payflow.banking.entity.OpenBankingAuthorization;
import com.payflow.banking.entity.BankingTransferType;
import com.payflow.banking.openbanking.OpenBankingTokenResponse;
import com.payflow.banking.openbanking.OpenBankingClient;
import com.payflow.banking.openbanking.OpenBankingAmbiguousException;
import com.payflow.banking.openbanking.OpenBankingDepositTransferRequest;
import com.payflow.banking.openbanking.OpenBankingProperties;
import com.payflow.banking.openbanking.OpenBankingRealNameInquiryRequest;
import com.payflow.banking.openbanking.OpenBankingReceiveInquiryRequest;
import com.payflow.banking.openbanking.OpenBankingTransferResponse;
import com.payflow.banking.openbanking.OpenBankingTransferResultRequest;
import com.payflow.banking.openbanking.OpenBankingTransferResultResponse;
import com.payflow.banking.openbanking.OpenBankingUserMeResponse;
import com.payflow.banking.openbanking.OpenBankingWithdrawTransferRequest;
import com.payflow.banking.repository.BankAccountRepository;
import com.payflow.banking.repository.BankingApiLogRepository;
import com.payflow.banking.repository.BankingTransferRepository;
import com.payflow.banking.repository.OpenBankingTokenRepository;
import com.payflow.banking.repository.OpenBankingAuthorizationRepository;
import com.payflow.banking.support.error.BusinessException;
import com.payflow.banking.support.error.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.net.URLEncoder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class BankingService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000");
    private static final String DEPOSIT_REFERENCE_TYPE = "OPEN_BANKING_CHARGE";
    private static final String WITHDRAWAL_REFERENCE_TYPE = "OPEN_BANKING_WITHDRAWAL";
    private static final String REFUND_REFERENCE_TYPE = "OPEN_BANKING_REFUND";
    private static final DateTimeFormatter OPENBANKING_DTIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final BankAccountRepository bankAccountRepository;
    private final BankingTransferRepository bankingTransferRepository;
    private final BankingApiLogRepository bankingApiLogRepository;
    private final OpenBankingTokenRepository openBankingTokenRepository;
    private final OpenBankingAuthorizationRepository openBankingAuthorizationRepository;
    private final WalletClient walletClient;
    private final OpenBankingClient openBankingClient;
    private final OpenBankingProperties openBankingProperties;
    private final TokenCryptoService tokenCryptoService;

    @Value("${internal.secret:}")
    private String internalSecret;

    @Transactional
    public BankAccountResponse createBankAccount(CreateBankAccountRequest request, Long requestUserId) {
        String bankCode = request.bankCode().trim();
        String accountNumber = normalizeAccountNumber(request.accountNumber());
        String accountNumberHash = sha256(accountNumber);
        String holderName = request.accountHolderName().trim();

        if (bankAccountRepository.existsByUserIdAndBankCodeAndAccountNumber(requestUserId, bankCode, accountNumberHash)) {
            throw new BusinessException(ErrorCode.DUPLICATE_BANK_ACCOUNT);
        }

        BankAccount account = bankAccountRepository.save(new BankAccount(
                requestUserId,
                bankCode,
                accountNumberHash,
                maskAccountNumber(accountNumber),
                holderName,
                trimToNull(request.fintechUseNum()),
                trimToNull(request.userSeqNo()),
                trimToNull(request.bankName()),
                trimToNull(request.inquiryAgreeYn()),
                trimToNull(request.transferAgreeYn())
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
                .orElseGet(() -> processNewDeposit(requestUserId, account, amount, normalizedIdempotencyKey, requestHash));
    }

    @Transactional
    public BankingTransferResponse createWithdrawal(CreateWithdrawalRequest request, String idempotencyKey, Long requestUserId) {
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        BigDecimal amount = normalizeAmount(request.amount());
        BankAccount account = bankAccountRepository.findByIdAndUserIdAndStatus(request.bankAccountId(), requestUserId, BankAccountStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.BANK_ACCOUNT_NOT_FOUND));

        String requestHash = createRequestHash("WITHDRAWAL", requestUserId, account.getId(), amount);
        return bankingTransferRepository.findByIdempotencyKey(normalizedIdempotencyKey)
                .map(existing -> resolveExistingTransfer(existing, requestHash))
                .orElseGet(() -> processNewWithdrawal(requestUserId, account, amount, normalizedIdempotencyKey, requestHash));
    }

    @Transactional(readOnly = true)
    public BankingTransferResponse getTransfer(Long bankingTransferId, Long requestUserId) {
        return bankingTransferRepository.findByIdAndUserId(bankingTransferId, requestUserId)
                .map(BankingTransferResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.BANKING_TRANSFER_NOT_FOUND));
    }

    @Transactional
    public OpenBankingAuthorizeUrlResponse createAuthorizeUrl(Long requestUserId) {
        requireOpenBankingAuthorizeConfig();
        String state = createState(requestUserId);
        openBankingAuthorizationRepository.save(new OpenBankingAuthorization(requestUserId, state));
        String url = openBankingProperties.baseUrl()
                + "/oauth/2.0/authorize"
                + "?response_type=code"
                + "&client_id=" + encode(openBankingProperties.clientId())
                + "&redirect_uri=" + encode(openBankingProperties.callbackUrl())
                + "&scope=" + encode("login inquiry transfer")
                + "&state=" + encode(state)
                + "&auth_type=0";
        return new OpenBankingAuthorizeUrlResponse(url, state);
    }

    private void requireOpenBankingAuthorizeConfig() {
        requiredOpenBankingConfig(openBankingProperties.baseUrl(), "baseUrl");
        requiredOpenBankingConfig(openBankingProperties.clientId(), "clientId");
        requiredOpenBankingConfig(openBankingProperties.callbackUrl(), "callbackUrl");
    }

    @Transactional
    public OpenBankingCallbackResponse handleOpenBankingCallback(OpenBankingCallbackRequest request, Long requestUserId) {
        validateState(request.state(), requestUserId);
        OpenBankingAuthorization authorization = openBankingAuthorizationRepository.findByState(request.state())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "OpenBanking authorization state was not requested."));
        OpenBankingTokenResponse token = openBankingClient.exchangeAuthorizationCode(request.code());
        if (token == null || !StringUtils.hasText(token.accessToken()) || !StringUtils.hasText(token.userSeqNo())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "OpenBanking token response is invalid.");
        }
        saveUserToken(requestUserId, token);
        String encryptedAccessToken = tokenCryptoService.encrypt(token.accessToken());
        String encryptedRefreshToken = tokenCryptoService.encrypt(token.refreshToken());
        authorization.connect(
                token.userSeqNo(),
                encryptedAccessToken,
                encryptedRefreshToken,
                token.expiresIn() == null ? null : LocalDateTime.now().plusSeconds(token.expiresIn())
        );
        List<BankAccountResponse> accounts = syncAccountsFromUserMe(
                requestUserId,
                openBankingClient.getUserMe(token.userSeqNo(), token.accessToken()),
                authorization.getId()
        );
        return new OpenBankingCallbackResponse(token.userSeqNo(), token.scope(), accounts);
    }

    @Transactional
    public OpenBankingCallbackResponse handleOpenBankingRedirect(String code, String state) {
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "OpenBanking callback code and state are required.");
        }
        OpenBankingAuthorization authorization = openBankingAuthorizationRepository.findByState(state)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "OpenBanking authorization state was not requested."));
        return handleOpenBankingCallback(new OpenBankingCallbackRequest(code, state), authorization.getUserId());
    }

    @Transactional
    public List<BankAccountResponse> syncOpenBankingAccounts(Long requestUserId) {
        OpenBankingToken token = getUserToken(requestUserId);
        String accessToken = tokenCryptoService.decrypt(token.getAccessTokenEncrypted());
        return syncAccountsFromUserMe(
                requestUserId,
                openBankingClient.getUserMe(token.getUserSeqNo(), accessToken),
                null
        );
    }

    public OpenBankingAttemptResponse attemptRealNameInquiry(OpenBankingRealNameInquiryRequest request) {
        openBankingClient.attemptRealNameInquiry(request);
        return new OpenBankingAttemptResponse("REAL_NAME_INQUIRY", true);
    }

    public OpenBankingAttemptResponse attemptReceiveInquiry(OpenBankingReceiveInquiryRequest request) {
        openBankingClient.attemptReceiveInquiry(request);
        return new OpenBankingAttemptResponse("RECEIVE_INQUIRY", true);
    }

    public OpenBankingAttemptResponse attemptDepositTransfer(OpenBankingDepositTransferRequest request) {
        openBankingClient.attemptDepositTransfer(request);
        return new OpenBankingAttemptResponse("DEPOSIT_TRANSFER", true);
    }

    @Transactional
    public BankingTransferResponse checkTransferResult(Long bankingTransferId, Long requestUserId) {
        BankingTransfer transfer = bankingTransferRepository.findByIdAndUserId(bankingTransferId, requestUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BANKING_TRANSFER_NOT_FOUND));
        if (transfer.getStatus() != BankingTransferStatus.BANK_PROCESSING
                && transfer.getStatus() != BankingTransferStatus.UNKNOWN) {
            return BankingTransferResponse.from(transfer);
        }
        return checkTransferResultInternal(transfer, requestUserId);
    }

    @Transactional
    public BankingTransferResponse compensateWithdrawal(Long bankingTransferId, Long requestUserId) {
        BankingTransfer transfer = bankingTransferRepository.findByIdAndUserId(bankingTransferId, requestUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BANKING_TRANSFER_NOT_FOUND));
        if (transfer.getTransferType() != BankingTransferType.WITHDRAWAL) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Only withdrawal transfers can be compensated.");
        }
        if (transfer.getStatus() == BankingTransferStatus.COMPENSATED) {
            return BankingTransferResponse.from(transfer);
        }
        if (transfer.getStatus() != BankingTransferStatus.COMPENSATION_REQUIRED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Transfer is not compensation-required.");
        }

        try {
            var wallet = walletClient.getWalletByUserId(requestUserId, true, internalSecret);
            walletClient.deposit(
                    wallet.walletId(),
                    new WalletBalanceChangeRequest(transfer.getAmount(), REFUND_REFERENCE_TYPE, transfer.getBankTranId()),
                    true,
                    internalSecret
            );
            transfer.markCompensated(wallet.walletId(), REFUND_REFERENCE_TYPE, transfer.getBankTranId());
            return BankingTransferResponse.from(transfer);
        } catch (RuntimeException exception) {
            transfer.recordCompensationFailure(resolveMessage(exception));
            return BankingTransferResponse.from(transfer);
        }
    }

    @Transactional
    public int checkDueTransferResults() {
        List<BankingTransfer> transfers = bankingTransferRepository
                .findTop20ByStatusInAndNextResultCheckAtLessThanEqualOrderByNextResultCheckAtAsc(
                        List.of(BankingTransferStatus.BANK_PROCESSING, BankingTransferStatus.UNKNOWN),
                        LocalDateTime.now()
                );
        for (BankingTransfer transfer : transfers) {
            checkTransferResultInternal(transfer, transfer.getUserId());
        }
        return transfers.size();
    }

    private BankingTransferResponse checkTransferResultInternal(BankingTransfer transfer, Long requestUserId) {
        if (!StringUtils.hasText(transfer.getBankTranDate())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "bankTranDate is required for transfer result check.");
        }
        transfer.recordResultCheck();

        OpenBankingTransferResultResponse result = openBankingClient.transferResult(
                new OpenBankingTransferResultRequest(
                        "1",
                        LocalDateTime.now().format(OPENBANKING_DTIME_FORMATTER),
                        transfer.getBankTranId(),
                        transfer.getBankTranDate(),
                        transfer.getAmount().toPlainString()
                ),
                getOrgAccessToken()
        );
        OpenBankingTransferResultResponse.ResultItem item = firstResultItem(result);
        saveApiLog(
                transfer.getId(),
                "TRANSFER_RESULT",
                transfer.getBankTranId(),
                result == null ? null : result.rspCode(),
                result == null ? null : result.apiTranId(),
                item == null ? null : item.bankRspCode(),
                "check_type,tran_dtime,req_cnt,req_list[].tran_no,req_list[].org_bank_tran_id,req_list[].org_bank_tran_date,req_list[].org_tran_amt",
                "api_tran_id,api_tran_dtm,rsp_code,rsp_message,res_cnt,res_list[].bank_tran_id,res_list[].bank_tran_date,res_list[].bank_rsp_code,res_list[].tran_amt",
                null
        );
        if (result != null && "A0000".equals(result.rspCode()) && item != null && "000".equals(item.bankRspCode())) {
            transfer.markBankSucceeded(item.bankTranDate(), result.apiTranId(), result.rspCode(), item.bankRspCode());
            reflectDepositToWallet(transfer, requestUserId);
        } else if (result != null && ("A0001".equals(result.rspCode()) || "A0007".equals(result.rspCode())
                || (item != null && isProcessingBankCode(item.bankRspCode())))) {
            transfer.markBankProcessing(
                    item == null ? transfer.getBankTranDate() : item.bankTranDate(),
                    result.rspCode(),
                    item == null ? null : item.bankRspCode(),
                    "OpenBanking transfer result is still not final."
            );
        } else {
            transfer.markBankFailed(
                    result == null ? null : result.rspCode(),
                    item == null ? null : item.bankRspCode(),
                    "OpenBanking transfer result failed."
            );
        }
        return BankingTransferResponse.from(transfer);
    }

    private BankingTransferResponse resolveExistingTransfer(BankingTransfer transfer, String requestHash) {
        if (!transfer.getRequestHash().equals(requestHash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_REQUEST_MISMATCH);
        }
        return BankingTransferResponse.from(transfer);
    }

    private BankingTransferResponse processNewDeposit(
            Long userId,
            BankAccount account,
            BigDecimal amount,
            String idempotencyKey,
            String requestHash
    ) {
        String bankTranId = createBankTranId(userId, idempotencyKey);
        BankingTransfer transfer;
        try {
            transfer = bankingTransferRepository.saveAndFlush(new BankingTransfer(
                    userId,
                    account.getId(),
                    amount,
                    idempotencyKey,
                    requestHash,
                    bankTranId,
                    BankingTransferType.CHARGE
            ));
        } catch (DataIntegrityViolationException exception) {
            return bankingTransferRepository.findByIdempotencyKey(idempotencyKey)
                    .map(existing -> resolveExistingTransfer(existing, requestHash))
                    .orElseThrow(() -> exception);
        }

        try {
            OpenBankingTransferResponse openBankingResponse = openBankingClient.withdrawTransfer(
                    toWithdrawTransferRequest(
                            transfer.getBankTranId(),
                            userId,
                            account,
                            amount
                    ),
                    getUserAccessToken(userId)
            );
            saveApiLog(
                    transfer.getId(),
                    "WITHDRAW_TRANSFER",
                    transfer.getBankTranId(),
                    openBankingResponse == null ? null : openBankingResponse.rspCode(),
                    openBankingResponse == null ? null : openBankingResponse.apiTranId(),
                    openBankingResponse == null ? null : openBankingResponse.bankRspCode(),
                    "bank_tran_id,cntr_account_type,cntr_account_num,dps_print_content,fintech_use_num,wd_print_content,tran_amt,tran_dtime,req_client_name,req_client_fintech_use_num,req_client_num,transfer_purpose",
                    "api_tran_id,api_tran_dtm,rsp_code,rsp_message,bank_tran_id,bank_tran_date,bank_code_tran,bank_rsp_code,bank_rsp_message,tran_amt",
                    null
            );
            if (openBankingResponse == null) {
                transfer.markBankProcessing(null, null, "OpenBanking response was empty. Transfer result check is required.");
                return BankingTransferResponse.from(transfer);
            }
            if (openBankingResponse.isSuccess()) {
                transfer.markBankSucceeded(
                        openBankingResponse.bankTranDate(),
                        openBankingResponse.apiTranId(),
                        openBankingResponse.rspCode(),
                        openBankingResponse.bankRspCode()
                );
            } else if (openBankingResponse.needsResultCheck()) {
                transfer.markBankProcessing(
                        openBankingResponse.bankTranDate(),
                        openBankingResponse.rspCode(),
                        openBankingResponse.bankRspCode(),
                        "OpenBanking transfer is not final. " + openBankingResponse.summary()
                );
                return BankingTransferResponse.from(transfer);
            } else {
                transfer.markBankFailed(
                        openBankingResponse.rspCode(),
                        openBankingResponse.bankRspCode(),
                        "OpenBanking transfer failed. " + openBankingResponse.summary()
                );
                return BankingTransferResponse.from(transfer);
            }

            reflectDepositToWallet(transfer, userId);
            return BankingTransferResponse.from(transfer);
        } catch (OpenBankingAmbiguousException exception) {
            saveApiLog(
                    transfer.getId(),
                    "WITHDRAW_TRANSFER",
                    transfer.getBankTranId(),
                    null,
                    null,
                    null,
                    "bank_tran_id,cntr_account_type,cntr_account_num,dps_print_content,fintech_use_num,wd_print_content,tran_amt,tran_dtime,req_client_name,req_client_fintech_use_num,req_client_num,transfer_purpose",
                    null,
                    resolveMessage(exception)
            );
            transfer.markBankProcessing(null, null, "OpenBanking response is ambiguous. Transfer result check is required.");
            return BankingTransferResponse.from(transfer);
        } catch (RuntimeException exception) {
            saveApiLog(
                    transfer.getId(),
                    "WITHDRAW_TRANSFER",
                    transfer.getBankTranId(),
                    null,
                    null,
                    null,
                    "bank_tran_id,cntr_account_type,cntr_account_num,dps_print_content,fintech_use_num,wd_print_content,tran_amt,tran_dtime,req_client_name,req_client_fintech_use_num,req_client_num,transfer_purpose",
                    null,
                    resolveMessage(exception)
            );
            transfer.fail(resolveMessage(exception));
            return BankingTransferResponse.from(transfer);
        }
    }

    private BankingTransferResponse processNewWithdrawal(
            Long userId,
            BankAccount account,
            BigDecimal amount,
            String idempotencyKey,
            String requestHash
    ) {
        String bankTranId = createBankTranId(userId, idempotencyKey);
        BankingTransfer transfer;
        try {
            transfer = bankingTransferRepository.saveAndFlush(new BankingTransfer(
                    userId,
                    account.getId(),
                    amount,
                    idempotencyKey,
                    requestHash,
                    bankTranId,
                    BankingTransferType.WITHDRAWAL
            ));
        } catch (DataIntegrityViolationException exception) {
            return bankingTransferRepository.findByIdempotencyKey(idempotencyKey)
                    .map(existing -> resolveExistingTransfer(existing, requestHash))
                    .orElseThrow(() -> exception);
        }

        boolean walletWithdrawn = false;
        try {
            var wallet = walletClient.getWalletByUserId(userId, true, internalSecret);
            transfer.markWalletWithdrawing(WITHDRAWAL_REFERENCE_TYPE, bankTranId);
            walletClient.withdraw(
                    wallet.walletId(),
                    new WalletBalanceChangeRequest(amount, WITHDRAWAL_REFERENCE_TYPE, bankTranId),
                    true,
                    internalSecret
            );
            walletWithdrawn = true;
            openBankingClient.attemptDepositTransfer(toDepositTransferAttemptRequest(bankTranId, userId, account, amount));
            saveApiLog(
                    transfer.getId(),
                    "DEPOSIT_TRANSFER_ATTEMPT",
                    bankTranId,
                    null,
                    null,
                    null,
                    "cntr_account_type,cntr_account_num,wd_pass_phrase,wd_print_content,name_check_option,tran_dtime,req_cnt,req_list[].bank_tran_id,req_list[].fintech_use_num,req_list[].tran_amt,req_list[].req_client_name,req_list[].req_client_num,req_list[].transfer_purpose",
                    null,
                    "No permission API. Response is intentionally ignored."
            );
            transfer.markCompensationRequired("OpenBanking deposit transfer is no-permission in this environment. Wallet withdrawal requires compensation.");
            return BankingTransferResponse.from(transfer);
        } catch (RuntimeException exception) {
            if (walletWithdrawn) {
                transfer.markCompensationRequired(resolveMessage(exception));
            } else {
                transfer.fail(resolveMessage(exception));
            }
            return BankingTransferResponse.from(transfer);
        }
    }

    private List<BankAccountResponse> syncAccountsFromUserMe(Long userId, OpenBankingUserMeResponse userMe) {
        return syncAccountsFromUserMe(userId, userMe, null);
    }

    private List<BankAccountResponse> syncAccountsFromUserMe(Long userId, OpenBankingUserMeResponse userMe, Long authorizationId) {
        if (userMe == null || !"A0000".equals(userMe.rspCode()) || userMe.resList() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "OpenBanking user info response is invalid.");
        }
        for (OpenBankingUserMeResponse.Account account : userMe.resList()) {
            if (!StringUtils.hasText(account.fintechUseNum())
                    || bankAccountRepository.existsByUserIdAndFintechUseNum(userId, account.fintechUseNum())) {
                continue;
            }
            String bankCode = defaultText(account.bankCodeStd(), "UNKNOWN");
            String maskedAccountNumber = defaultText(account.accountNumMasked(), "****");
            String accountKeyHash = sha256("openbanking:" + account.fintechUseNum());
            BankAccount saved = bankAccountRepository.save(new BankAccount(
                    userId,
                    bankCode,
                    accountKeyHash,
                    maskedAccountNumber,
                    defaultText(account.accountHolderName(), defaultText(userMe.userName(), "UNKNOWN")),
                    account.fintechUseNum(),
                    userMe.userSeqNo(),
                    trimToNull(account.bankName()),
                    trimToNull(account.inquiryAgreeYn()),
                    trimToNull(account.transferAgreeYn())
            ));
            saved.markOpenBankingAuthorization(
                    authorizationId,
                    tokenCryptoService.encrypt(account.fintechUseNum()),
                    trimToNull(account.accountAlias())
            );
        }
        return getBankAccounts(userId);
    }

    private void reflectDepositToWallet(BankingTransfer transfer, Long userId) {
        transfer.markWalletReflecting(DEPOSIT_REFERENCE_TYPE, transfer.getBankTranId());
        var wallet = walletClient.getWalletByUserId(userId, true, internalSecret);
        walletClient.deposit(
                wallet.walletId(),
                new WalletBalanceChangeRequest(transfer.getAmount(), DEPOSIT_REFERENCE_TYPE, transfer.getBankTranId()),
                true,
                internalSecret
        );
        transfer.succeed(wallet.walletId(), null);
    }

    private void saveUserToken(Long userId, OpenBankingTokenResponse tokenResponse) {
        OpenBankingToken token = openBankingTokenRepository.findByUserIdAndTokenType(userId, OpenBankingTokenType.USER)
                .orElseGet(() -> new OpenBankingToken(userId, OpenBankingTokenType.USER));
        token.update(
                tokenResponse.userSeqNo(),
                tokenResponse.clientUseCode(),
                tokenCryptoService.encrypt(tokenResponse.accessToken()),
                tokenCryptoService.encrypt(tokenResponse.refreshToken()),
                tokenResponse.scope(),
                tokenResponse.expiresIn() == null ? null : LocalDateTime.now().plusSeconds(tokenResponse.expiresIn())
        );
        openBankingTokenRepository.save(token);
    }

    private OpenBankingToken getUserToken(Long userId) {
        OpenBankingToken token = openBankingTokenRepository.findByUserIdAndTokenType(userId, OpenBankingTokenType.USER)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "OpenBanking user token is required."));
        if (token.getExpiresAt() != null && token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "OpenBanking user token is expired.");
        }
        return token;
    }

    private String getUserAccessToken(Long userId) {
        if (!"real".equalsIgnoreCase(openBankingProperties.mode())) {
            return "mock-user-token";
        }
        return tokenCryptoService.decrypt(getUserToken(userId).getAccessTokenEncrypted());
    }

    private String getOrgAccessToken() {
        String envToken = openBankingProperties.effectiveOrgAccessToken();
        if (StringUtils.hasText(envToken)) {
            return envToken;
        }
        if (!"real".equalsIgnoreCase(openBankingProperties.mode())) {
            return "mock-org-token";
        }
        OpenBankingToken token = openBankingTokenRepository.findByUserIdAndTokenType(null, OpenBankingTokenType.ORG)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "OpenBanking org token is required."));
        return tokenCryptoService.decrypt(token.getAccessTokenEncrypted());
    }

    private OpenBankingTransferResultResponse.ResultItem firstResultItem(OpenBankingTransferResultResponse result) {
        if (result == null || result.resList() == null || result.resList().isEmpty()) {
            return null;
        }
        return result.resList().getFirst();
    }

    private boolean isProcessingBankCode(String bankRspCode) {
        return "400".equals(bankRspCode)
                || "803".equals(bankRspCode)
                || "804".equals(bankRspCode)
                || "819".equals(bankRspCode)
                || "822".equals(bankRspCode);
    }

    private void saveApiLog(
            Long bankingTransferId,
            String apiName,
            String requestId,
            String apiResponseCode,
            String apiTranId,
            String bankResponseCode,
            String requestKeys,
            String responseKeys,
            String errorMessage
    ) {
        bankingApiLogRepository.save(new BankingApiLog(
                bankingTransferId,
                apiName,
                requestId,
                null,
                apiResponseCode,
                apiTranId,
                bankResponseCode,
                requestKeys,
                responseKeys,
                errorMessage
        ));
    }

    private OpenBankingWithdrawTransferRequest toWithdrawTransferRequest(
            String bankTranId,
            Long userId,
            BankAccount account,
            BigDecimal amount
    ) {
        String fintechUseNum = requiredForRealMode(account.getFintechUseNum(), "fintechUseNum");
        String cntrAccountNum = requiredForRealMode(openBankingProperties.cntrAccountNum(), "cntrAccountNum");
        String tranAmt = amount.toPlainString();
        return new OpenBankingWithdrawTransferRequest(
                bankTranId,
                openBankingProperties.effectiveCntrAccountType(),
                cntrAccountNum,
                defaultText(openBankingProperties.chargeDpsPrintContent(), "PAYFLOW_CHARGE"),
                fintechUseNum,
                defaultText(openBankingProperties.chargeWdPrintContent(), "PAYFLOW"),
                tranAmt,
                LocalDateTime.now().format(OPENBANKING_DTIME_FORMATTER),
                account.getAccountHolderName(),
                fintechUseNum,
                "USER" + String.format("%015d", userId),
                openBankingProperties.effectiveTransferPurpose()
        );
    }

    private OpenBankingDepositTransferRequest toDepositTransferAttemptRequest(
            String bankTranId,
            Long userId,
            BankAccount account,
            BigDecimal amount
    ) {
        String fintechUseNum = requiredForRealMode(account.getFintechUseNum(), "fintechUseNum");
        String cntrAccountNum = requiredForRealMode(openBankingProperties.cntrAccountNum(), "cntrAccountNum");
        return new OpenBankingDepositTransferRequest(
                openBankingProperties.effectiveCntrAccountType(),
                cntrAccountNum,
                openBankingProperties.effectiveWdPassPhrase(),
                defaultText(openBankingProperties.withdrawalWdPrintContent(), "PAYFLOW_WITHDRAWAL"),
                "on",
                LocalDateTime.now().format(OPENBANKING_DTIME_FORMATTER),
                bankTranId,
                fintechUseNum,
                "PAYFLOW_WITHDRAWAL",
                amount.toPlainString(),
                account.getAccountHolderName(),
                fintechUseNum,
                "USER" + String.format("%015d", userId),
                openBankingProperties.effectiveTransferPurpose(),
                "PAYFLOW_WITHDRAWAL",
                null
        );
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
        return createRequestHash("CHARGE", userId, bankAccountId, amount);
    }

    private String createRequestHash(String type, Long userId, Long bankAccountId, BigDecimal amount) {
        return sha256(type + ":" + userId + ":" + bankAccountId + ":" + amount.toPlainString());
    }

    private String createBankTranId(Long userId, String idempotencyKey) {
        String clientUseCode = trimToNull(openBankingProperties.clientUseCode());
        if (clientUseCode == null || "mock".equalsIgnoreCase(openBankingProperties.mode())) {
            return "MOCK-" + userId + "-" + sha256(idempotencyKey).substring(0, 24);
        }
        String suffix = sha256(userId + ":" + idempotencyKey).substring(0, 9).toUpperCase();
        return clientUseCode + "U" + suffix;
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

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String createState(Long userId) {
        String timestamp = Long.toString(System.currentTimeMillis());
        return timestamp + "." + stateSignature(userId, timestamp);
    }

    private void validateState(String state, Long userId) {
        if (!StringUtils.hasText(state) || !state.contains(".")) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "OpenBanking state is invalid.");
        }
        String[] parts = state.split("\\.", 2);
        long createdAtMillis;
        try {
            createdAtMillis = Long.parseLong(parts[0]);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "OpenBanking state is invalid.");
        }
        if (System.currentTimeMillis() - createdAtMillis > 10 * 60 * 1000L) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "OpenBanking state is expired.");
        }
        if (parts.length != 2 || !stateSignature(userId, parts[0]).equals(parts[1])) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "OpenBanking state is invalid.");
        }
    }

    private String stateSignature(Long userId, String timestamp) {
        return sha256(userId + ":" + timestamp + ":" + defaultText(internalSecret, "openbanking-state")).substring(0, 32);
    }

    private String requiredForRealMode(String value, String name) {
        if (!"real".equalsIgnoreCase(openBankingProperties.mode())) {
            return value;
        }
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("OpenBanking " + name + " is required for real mode");
        }
        return value.trim();
    }

    private String requiredOpenBankingConfig(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "OpenBanking " + name + " is required.");
        }
        return value.trim();
    }
}
