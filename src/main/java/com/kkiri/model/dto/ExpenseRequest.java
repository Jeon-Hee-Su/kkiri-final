package com.kkiri.model.dto;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data                   // Getter, Setter, toString, equals, hashCode 자동 생성
@NoArgsConstructor      // 기본 생성자 자동 생성
@AllArgsConstructor     // 모든 필드를 파라미터로 받는 생성자 자동 생성
public class ExpenseRequest {
	private Integer expenseId;
    private String merchantName;
    private String category;
    private Integer amount;
    private Integer groupId; 
    private Integer paidBy;
    private String fileName;
    private Long transactionId;   // 거래내역에서 연동 시 전달받는 값 (null 허용)
    private List<ItemRequest> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemRequest {
        private String itemName;
        private Integer price;
        private Integer quantity;
    }
}