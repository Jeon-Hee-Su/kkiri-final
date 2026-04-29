package com.kkiri.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.sql.Timestamp;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserVO {
    // USERS 테이블 컬럼
    private int userId;
    private String email;
    private String loginId;
    private String password;
    private String nickname;
    private String profileImage;
    private String status;
    private String isVerified;
    private Timestamp verifiedAt;
    private Timestamp createdAt;
    private Timestamp deletedAt;
    private String role;
    
    // 1:1 관계 (USER_PRIVACY 테이블)
    private UserPrivacyVO userPrivacy;
    
    // 1:N 관계 (SOCIAL_ACCOUNTS 테이블)
    private List<SocialAccountVO> socialAccounts;
    
    // 회비 미납 여부 필드
    private boolean isUnpaid;

    // Thymeleaf 인식 오류 방지를 위한 명시적 Getter
    public boolean getIsUnpaid() {
        return this.isUnpaid;
    }

    // 기본 Getter (Lombok 관례)
    public boolean isUnpaid() {
        return this.isUnpaid;
    }

    // Setter
    public void setIsUnpaid(boolean isUnpaid) {
        this.isUnpaid = isUnpaid;
    }
}