package com.kkiri.model.vo;

import lombok.Data;
import java.sql.Timestamp;

@Data
public class GroupMemberVO {
    // 유저 기본 정보
    private int userId;
    private String userName;
    private String userEmail;
    private String profileImg;

    // 그룹 내 멤버 정보 (GROUP_MEMBERS 테이블 컬럼)
    private int mappingId;
    private int groupId;
    private String groupRole;           // HOST, MEMBER 등
    private Timestamp joinedAt;
    private boolean isCurrentUserHost;
    private String memberStatus;        // 'JOINED', 'KICKED' 등
    private boolean isHostUser;

    // ★ 추가: 유저계좌 ↔ 그룹멤버 1대1 매핑
    private Integer accountId;          // USER_ACCOUNTS.ACCOUNT_ID (빌링키 자동이체용)
}