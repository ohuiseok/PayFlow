package com.payflow.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.payflow.wallet.dto.CreateWalletRequest;
import com.payflow.wallet.dto.WalletBalanceChangeRequest;
import com.payflow.wallet.entity.Wallet;
import com.payflow.wallet.entity.WalletReferenceType;
import com.payflow.wallet.entity.WalletTransactionType;
import com.payflow.wallet.repository.WalletRepository;
import com.payflow.wallet.repository.WalletTransactionRepository;
import com.payflow.wallet.support.error.BusinessException;
import com.payflow.wallet.support.error.ErrorCode;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WalletServiceTest {

    @Autowired
    WalletService walletService;

    @Autowired
    WalletRepository walletRepository;

    @Autowired
    WalletTransactionRepository walletTransactionRepository;

    @BeforeEach
    void setUp() {
        walletTransactionRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    void createWalletReturnsZeroBalance() {
        var response = walletService.createWallet(new CreateWalletRequest(1L), 1L);

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.status().name()).isEqualTo("ACTIVE");
    }

    @Test
    void createWalletRejectsDuplicateUserId() {
        walletService.createWallet(new CreateWalletRequest(1L), 1L);

        assertThatThrownBy(() -> walletService.createWallet(new CreateWalletRequest(1L), 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_WALLET);
    }

    @Test
    void createWalletRejectsOwnerMismatch() {
        assertThatThrownBy(() -> walletService.createWallet(new CreateWalletRequest(1L), 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_OWNER_MISMATCH);
    }

    @Test
    void depositSavesTransaction() {
        var wallet = walletService.createWallet(new CreateWalletRequest(1L), 1L);

        var response = walletService.deposit(
                wallet.walletId(),
                new WalletBalanceChangeRequest(new BigDecimal("10000"), WalletReferenceType.MANUAL_CHARGE, "1"),
                1L,
                false
        );

        assertThat(response.balance()).isEqualByComparingTo("10000");
        assertThat(walletTransactionRepository.findByWalletIdAndTransactionTypeAndReferenceTypeAndReferenceId(
                wallet.walletId(),
                WalletTransactionType.DEPOSIT,
                "MANUAL_CHARGE",
                "1"
        )).isPresent();
    }

    @Test
    void duplicateDepositReferenceDoesNotIncreaseAgain() {
        var wallet = walletService.createWallet(new CreateWalletRequest(1L), 1L);
        var request = new WalletBalanceChangeRequest(new BigDecimal("10000"), WalletReferenceType.MANUAL_CHARGE, "1");

        walletService.deposit(wallet.walletId(), request, 1L, false);
        var response = walletService.deposit(wallet.walletId(), request, 1L, false);

        assertThat(response.balance()).isEqualByComparingTo("10000");
        assertThat(walletRepository.findById(wallet.walletId()).orElseThrow().getBalance()).isEqualByComparingTo("10000");
    }

    @Test
    void duplicateReferenceWithDifferentAmountFails() {
        var wallet = walletService.createWallet(new CreateWalletRequest(1L), 1L);

        walletService.deposit(
                wallet.walletId(),
                new WalletBalanceChangeRequest(new BigDecimal("10000"), WalletReferenceType.MANUAL_CHARGE, "1"),
                1L,
                false
        );

        assertThatThrownBy(() -> walletService.deposit(
                wallet.walletId(),
                new WalletBalanceChangeRequest(new BigDecimal("20000"), WalletReferenceType.MANUAL_CHARGE, "1"),
                1L,
                false
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_WALLET_REFERENCE);
    }

    @Test
    void withdrawDecreasesBalanceOnceForSameReference() {
        var wallet = walletService.createWallet(new CreateWalletRequest(1L), 1L);
        walletService.deposit(
                wallet.walletId(),
                new WalletBalanceChangeRequest(new BigDecimal("10000"), WalletReferenceType.MANUAL_CHARGE, "1"),
                1L,
                false
        );
        var request = new WalletBalanceChangeRequest(new BigDecimal("3000"), WalletReferenceType.TRANSFER, "1001");

        walletService.withdraw(wallet.walletId(), request);
        var response = walletService.withdraw(wallet.walletId(), request);

        assertThat(response.balance()).isEqualByComparingTo("7000");
        assertThat(walletRepository.findById(wallet.walletId()).orElseThrow().getBalance()).isEqualByComparingTo("7000");
    }

    @Test
    void withdrawRejectsInsufficientBalance() {
        var wallet = walletService.createWallet(new CreateWalletRequest(1L), 1L);

        assertThatThrownBy(() -> walletService.withdraw(
                wallet.walletId(),
                new WalletBalanceChangeRequest(new BigDecimal("1"), WalletReferenceType.TRANSFER, "1001")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
    }

    @Test
    void lockedWalletRejectsWithdraw() {
        var wallet = walletService.createWallet(new CreateWalletRequest(1L), 1L);
        Wallet entity = walletRepository.findById(wallet.walletId()).orElseThrow();
        entity.lock();
        walletRepository.saveAndFlush(entity);

        assertThatThrownBy(() -> walletService.withdraw(
                wallet.walletId(),
                new WalletBalanceChangeRequest(new BigDecimal("1"), WalletReferenceType.TRANSFER, "1001")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WALLET_LOCKED);
    }

    @Test
    void getWalletRejectsOwnerMismatch() {
        var wallet = walletService.createWallet(new CreateWalletRequest(1L), 1L);

        assertThatThrownBy(() -> walletService.getWallet(wallet.walletId(), 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_OWNER_MISMATCH);
    }

    @Test
    void rejectDecimalAmount() {
        var wallet = walletService.createWallet(new CreateWalletRequest(1L), 1L);

        assertThatThrownBy(() -> walletService.deposit(
                wallet.walletId(),
                new WalletBalanceChangeRequest(new BigDecimal("1.5"), WalletReferenceType.MANUAL_CHARGE, "1"),
                1L,
                false
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void concurrentWithdrawDoesNotMakeNegativeBalance() throws Exception {
        var wallet = walletService.createWallet(new CreateWalletRequest(1L), 1L);
        walletService.deposit(
                wallet.walletId(),
                new WalletBalanceChangeRequest(new BigDecimal("100"), WalletReferenceType.MANUAL_CHARGE, "1"),
                1L,
                false
        );

        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(2);

        for (int i = 0; i < 2; i++) {
            int reference = i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    walletService.withdraw(
                            wallet.walletId(),
                            new WalletBalanceChangeRequest(new BigDecimal("80"), WalletReferenceType.TRANSFER, "T-" + reference)
                    );
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(1, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdownNow();

        assertThat(walletRepository.findById(wallet.walletId()).orElseThrow().getBalance()).isEqualByComparingTo("20");
    }
}
