package com.kkiri.model.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SettlementVO {
    private int     settlementId;
    private Integer expenseId;          // nullable
    private int     groupId;
    private String  title;
    private long    totalAmount;
    private long    totalPenaltyAmount;
    private String  status;             // PROCEEDING / COMPLETED
    private String  createdAt;          // TO_CHAR 결과를 String으로 받음

    // 조회 편의용 (DB 컬럼 아님 — MyBatis resultType에서 자동 매핑)
    private List<SettlementDetailVO> details;
    private int paidCount;
    private int totalCount;
}