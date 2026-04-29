package com.kkiri.model.dto;

import lombok.Data;

@Data
public class PaymentRequestDTO {
    private String imp_uid;          // 포트원 결제 고유 번호
    private String merchant_uid;     // 우리 서버 주문 번호
    private Long amount;             // 결제 금액
    private Long groupId;            // 모임 번호
    private String description;      // 거래 내용 (예: "카카오페이 충전")
}