package com.kkiri.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.sql.Timestamp;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupAccountVO {
    private int accountId;          // ACCOUNT_ID (PK, 자동증가)
    private int groupId;            // GROUP_ID (FK, 모임 식별자)
    private String bankCode;        // BANK_CODE (은행코드)
    private String accountNumber;   // ACCOUNT_NUMBER (계좌번호)
    private long balance;           // BALANCE (잔액, 기본값 0)
    private String paymentPassword; // PAYMENT_PASSWORD (결제 비밀번호 6자리)
    private LocalDateTime createdAt;    // CREATED_AT (생성일시)
}