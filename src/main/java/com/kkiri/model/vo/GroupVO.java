package com.kkiri.model.vo;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * [KKIRI2] GROUPS 테이블 1:1 매핑 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupVO {

    // GROUP_ID (NUMBER) : PK
    private int groupId; 

    // GROUP_NAME (VARCHAR2(200))
    private String groupName;

    // INVITE_CODE (VARCHAR2(100))
    private String inviteCode;

    // CATEGORY (VARCHAR2(50))
    private String category;

    // STATUS (VARCHAR2(20)) : 'ACTIVE', 'DELETED' 등
    private String status;

    // CREATED_AT (TIMESTAMP(6))
    private LocalDateTime createdAt;

    // UPDATED_AT (TIMESTAMP(6))
    private LocalDateTime updatedAt;

    // DELETED_AT (TIMESTAMP(6))
    private LocalDateTime deletedAt;
    
    private String bankName;      

    private String accountNumber; 
    
    private int dueAmount;      // 정기 회비 (SUBSCRIPTION_FEE)
    private int regularDay;     // 납입일 (DUE_DAY)
    private String penaltyAmount;  // 벌금 (PENALTY_POLICY)
    private int penaltyDay;
    private String userId;
}