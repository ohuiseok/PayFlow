package com.payflow.transfer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.payflow.transfer.client.WalletBalanceChangeRequest;
import com.payflow.transfer.client.WalletClient;
import com.payflow.transfer.client.WalletResponse;
import com.payflow.transfer.dto.CreateTransferRequest;
import com.payflow.transfer.entity.TransferStatus;
import com.payflow.transfer.event.TransferEventPublisher;
import com.payflow.transfer.repository.TransferRepository;
import com.payflow.transfer.support.error.BusinessException;
import com.payflow.transfer.support.error.ErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class TransferServiceTest {

    @Autowired
    TransferService transferService;

    @Autowired
    TransferRepository transferRepository;

    @MockitoBean
    WalletClient walletClient;

    @MockitoBean
    TransferEventPublisher transferEventPublisher;

    @BeforeEach
    void setUp() {
        transferRepository.deleteAll();
        when(walletClient.getWalletByUserId(eq(1L), eq(true), any())).thenReturn(new WalletResponse(10L, 1L, new BigDecimal("10000"), "ACTIVE"));
        when(walletClient.getWalletByUserId(eq(2L), eq(true), any())).thenReturn(new WalletResponse(20L, 2L, BigDecimal.ZERO, "ACTIVE"));
        when(walletClient.withdraw(eq(10L), any(WalletBalanceChangeRequest.class), eq(true), any()))
                .thenReturn(new WalletResponse(10L, 1L, new BigDecimal("7000"), "ACTIVE"));
        when(walletClient.deposit(eq(20L), any(WalletBalanceChangeRequest.class), eq(true), any()))
                .thenReturn(new WalletResponse(20L, 2L, new BigDecimal("3000"), "ACTIVE"));
    }

    @Test
    void createTransferMovesMoneyAndRecordsSucceededStatus() {
        var response = transferService.createTransfer(new CreateTransferRequest(2L, new BigDecimal("3000")), "key-1", 1L);

        assertThat(response.senderUserId()).isEqualTo(1L);
        assertThat(response.receiverUserId()).isEqualTo(2L);
        assertThat(response.amount()).isEqualByComparingTo("3000");
        assertThat(response.status()).isEqualTo(TransferStatus.SUCCEEDED);
        verify(walletClient).withdraw(eq(10L), any(WalletBalanceChangeRequest.class), eq(true), any());
        verify(walletClient).deposit(eq(20L), any(WalletBalanceChangeRequest.class), eq(true), any());
        verify(transferEventPublisher).publishCompleted(any());
    }

    @Test
    void duplicateIdempotencyKeyWithSameRequestReturnsExistingResult() {
        transferService.createTransfer(new CreateTransferRequest(2L, new BigDecimal("3000")), "key-1", 1L);
        var response = transferService.createTransfer(new CreateTransferRequest(2L, new BigDecimal("3000")), "key-1", 1L);

        assertThat(response.status()).isEqualTo(TransferStatus.SUCCEEDED);
        assertThat(transferRepository.count()).isEqualTo(1);
        verify(walletClient, times(1)).withdraw(eq(10L), any(WalletBalanceChangeRequest.class), eq(true), any());
    }

    @Test
    void duplicateIdempotencyKeyWithDifferentRequestFails() {
        transferService.createTransfer(new CreateTransferRequest(2L, new BigDecimal("3000")), "key-1", 1L);

        assertThatThrownBy(() -> transferService.createTransfer(new CreateTransferRequest(2L, new BigDecimal("4000")), "key-1", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IDEMPOTENCY_REQUEST_MISMATCH);
    }

    @Test
    void createTransferRejectsSelfTransfer() {
        assertThatThrownBy(() -> transferService.createTransfer(new CreateTransferRequest(1L, new BigDecimal("3000")), "key-1", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void getTransferRejectsNonParticipant() {
        var response = transferService.createTransfer(new CreateTransferRequest(2L, new BigDecimal("3000")), "key-1", 1L);

        assertThatThrownBy(() -> transferService.getTransfer(response.transferId(), 3L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_OWNER_MISMATCH);
    }
}
