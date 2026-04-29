package com.kkiri.model.vo;

import lombok.Data;
import java.sql.Timestamp;

@Data // Getter, Setter, ToString 등을 자동으로 생성해줍니다 (Lombok 설치된 경우)
public class TransactionVO {
    private Long transactionId;         // transaction_id
    private Long settlementDetailId;    // settlement_detail_id (null 허용)
    private Long sourceAccountId;       // source_account_id (null 허용)
    private Long targetAccountId;       // target_account_id
    private Long amount;                // amount
    private String transactionType;     // transaction_type (V_ACCOUNT_DEPOSIT, PAYMENT 등)
    private String status;              // status (PENDING, SUCCESS 등)
    private String paymentReferenceUid; // payment_reference_uid (merchant_uid)
    private String description;         // description
    private Timestamp createdAt;        // created_at
    private Long balanceAfter;           // 거래 후 잔액 (group-history 표시용)
    private String category;             // 거래 유형 카테고리 (표시용)
    private Integer expenseId;           // 연결된 영수증 ID (null이면 영수증 없음) 
}