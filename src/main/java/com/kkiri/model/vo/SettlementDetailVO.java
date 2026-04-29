package com.kkiri.model.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SettlementDetailVO {
    private int     detailId;
    private int     settlementId;
    private int     userId;
    private String  userName;           // JOIN 조회용
    private String  profileImg;         // JOIN 조회용
    private long    amountDue;
    private long    penaltyAmount;
    private String  status;             // WAITING / PAID / CANCELLED
    private String  paidAt;             // TO_CHAR 결과를 String으로 받음
    private String  updatedAt;          // TO_CHAR 결과를 String으로 받음
    private String  settlementTitle;    // JOIN 조회용 (알림 표시)
    private int     groupId;            // JOIN 조회용
}