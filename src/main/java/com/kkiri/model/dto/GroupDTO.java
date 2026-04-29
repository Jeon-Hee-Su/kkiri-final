package com.kkiri.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class GroupDTO {

    // 모임 가입 요청 (초대코드 + 계좌번호 + ★계좌ID)
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JoinRequest {
        private String inviteCode;
        private String accountNumber;
        private Integer accountId;  // ★ 추가: USER_ACCOUNTS.ACCOUNT_ID (빌링키 자동이체용)
    }

    // 가입 결과 응답
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JoinResponse {
        private int groupId;
        private String message;
    }

    // 그룹 목록 간략 정보
    @Data
    public static class GroupListResponseLong {
        private int groupId;
        private String groupName;
    }

    // 그룹 상세 정보 (메인 대시보드용)
    @Data
    public static class GroupDetailResponse {
        private int groupId;
        private String groupName;
        private String accountNumber;
        private String bankName;
        private int balance;
        private int dueAmount;
        private int regularDay;
        private int penaltyAmount;
        private int penaltyDay;
        private String category;
    }

    // 회비 채우기(입금) 요청 객체
    @Data
    public static class GroupFillRequest {
        private int groupId;
        private int amount;
        private String accountNumber;
    }
}