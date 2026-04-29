package com.kkiri.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kkiri.mapper.PaymentMapper;
import com.kkiri.model.dto.PaymentRequestDTO;
import com.kkiri.model.vo.TransactionVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentMapper paymentMapper;

    /**
     * 기존 PortOne 충전 처리 (TRANSACTIONS 기록 포함)
     */
    @Transactional
    public void processCharge(PaymentRequestDTO dto) {
        paymentMapper.updateGroupAccountBalance(dto.getGroupId(), dto.getAmount());

        TransactionVO tx = new TransactionVO();
        tx.setTargetAccountId(dto.getGroupId());
        tx.setAmount(dto.getAmount());
        tx.setTransactionType("V_ACCOUNT_DEPOSIT");
        tx.setStatus("SUCCESS");
        tx.setPaymentReferenceUid(dto.getMerchant_uid());
        tx.setDescription(dto.getDescription());
        paymentMapper.insertTransaction(tx);
    }

    /**
     * QR 현장결제:
     * 1. GROUP_ACCOUNT.BALANCE 차감
     * 2. TRANSACTIONS 테이블에 결제 내역 기록
     *
     * @param groupId   결제 대상 그룹 ID
     * @param amount    결제 금액
     * @param groupName 그룹명 (DESCRIPTION 표시용, 없으면 groupId 사용)
     */
    @Transactional
    public void deductGroupBalance(int groupId, long amount, String groupName) {

        // 1. 잔액 차감 (WHERE BALANCE >= amount 조건 → 부족 시 0건)
        int updated = paymentMapper.deductGroupAccountBalance(groupId, amount);
        if (updated == 0) {
            throw new RuntimeException("잔액이 부족하거나 그룹 계좌를 찾을 수 없습니다.");
        }

        // 2. TRANSACTIONS 테이블에 현장결제 내역 기록
        //    - PAYMENT_REFERENCE_UID: 'QR_그룹ID_타임스탬프' 형태로 고유값 생성
        //    - DESCRIPTION: '현장결제 - {그룹명}' 형태
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        String referenceUid = "QR_" + groupId + "_" + timestamp;

        String displayName = (groupName != null && !groupName.isBlank())
                ? groupName
                : "그룹 #" + groupId;
        String description = "현장결제 - " + displayName;

        paymentMapper.insertQrTransaction(groupId, amount, referenceUid, description);

        log.info("QR 현장결제 완료 - groupId: {}, amount: {}, ref: {}", groupId, amount, referenceUid);
    }
}
