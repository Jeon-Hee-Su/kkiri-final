package com.kkiri.model.dto;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder // 서비스 레이어에서 객체를 생성할 때 매우 편리합니다.
public class ExpenseResponse {
    private Long expenseId;      // DB에서 생성된 PK 값
    private String merchantName;    // 상호명
    private String category;        // 카테고리
    private Long amount;         // 총 금액
    private String payDate;         // 결제 날짜 (날짜 형식을 "2026-03-17" 형태의 문자열로 변환)
    private String receiptImageUrl; // DB에 저장된 파일 이름 (예: receipt_123.jpg)
    private List<ItemResponse> items; // 상세 품목 리스트

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemResponse {
        private String itemName;
        private Integer price;
        private Integer quantity;
    }
}