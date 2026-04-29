package com.kkiri.model.dto;

import org.springframework.security.crypto.password.PasswordEncoder;

import com.kkiri.model.vo.UserPrivacyVO;
import com.kkiri.model.vo.UserVO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class AuthDTO {

    // 1. 자체 회원가입 요청 DTO
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignUpRequest {
        @NotBlank(message = "아이디는 필수입니다.")
        @Pattern(regexp = "^[a-zA-Z0-9]{4,20}$", message = "아이디는 영문, 숫자 조합 4~20자리만 가능합니다. (@ 사용 불가)")
        private String loginId;
        
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        private String email;

        @NotBlank(message = "비밀번호는 필수입니다.")
        private String password;

        @NotBlank(message = "이름은 필수입니다.")
        private String name;
        
        private String phoneNumber;
        private String birthDate; // 예: 990101

        @NotBlank(message = "본인인증 식별자(CI)는 필수입니다.")
        private String ci;
        
        private String di; 
        
        // 🚨 USERS 테이블용 VO로 변환 (role 삭제, nickname 추가)
        public UserVO toUserVO(PasswordEncoder passwordEncoder) {
        	
        		
            return UserVO.builder()
                    .loginId(this.loginId)
                    .email(this.email)
                    .password(passwordEncoder.encode(this.password)) // 암호화
                    .nickname(this.name) // 닉네임을 따로 안 받으므로 우선 이름을 닉네임으로 저장
                    .build();
        }

        // USER_PRIVACY 테이블용 VO로 변환
        public UserPrivacyVO toUserPrivacyVO(int userId) {
            return UserPrivacyVO.builder()
                    .userId(userId) 
                    .ci(this.ci)
                    .name(this.name)
                    .phoneNumber(this.phoneNumber)
                    .birthDate(this.birthDate)
                    .build();
        }
    }

    // 2. 일반 로그인 요청 DTO
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        @NotBlank(message = "아이디를 입력해주세요.")
        private String loginId;

        @NotBlank(message = "비밀번호를 입력해주세요.")
        private String password;
    }

    // 3. 소셜 로그인/가입 요청 DTO
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SocialLoginRequest {
        private String email;
        private String name;
        private String provider;    
        private String providerId;  
        private String profileImage;
    }

    // 4. 로그인 성공 시 프론트엔드에 돌려줄 응답 DTO
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthResponse {
        private String accessToken;  
        private String tokenType;    
        private String email;
        private String name;
        private String role; // (프론트엔드 호환성을 위해 남겨둠)     
        private String profileImage;
    }

    // 5. 소셜 로그인 후 추가 정보 입력(회원가입 완료) 전용 DTO
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SocialSignUpCompletionRequest {
        @NotBlank(message = "이름은 필수입니다.")
        private String name;
        
        private String phoneNumber;
        private String birthDate;
        
        @NotBlank(message = "본인인증 식별자(CI)는 필수입니다.")
        private String ci;
        private String di;
        
        @NotBlank(message = "토큰이 누락되었습니다.")
        private String tempToken;
        
        // 🚨 소셜 가입용 USERS 테이블 VO 변환 (role 삭제, nickname 추가)
        public UserVO toUserVO(String email) {
            return UserVO.builder()
                    .loginId(email)  // 소셜은 이메일을 아이디로 씀
                    .email(email)
                    .nickname(this.name) // 닉네임 기본값으로 이름 지정
                    .build();
        }

        // 소셜 가입용 USER_PRIVACY 테이블 VO 변환
        public UserPrivacyVO toUserPrivacyVO(int userId) {
            return UserPrivacyVO.builder()
                    .userId(userId)
                    .ci(this.ci)
                    .di(this.di)
                    .name(this.name)
                    .phoneNumber(this.phoneNumber)
                    .birthDate(this.birthDate)
                    .build();
        }
    }
    
    // 6. 프로필 업데이트 DTO
    @Data
    public static class UpdateProfileRequest {
    	private String userId;
        private String name;
        private String email;
        private String phone;
        private String birth;
        private String nickname;
    }
    
    // 7. 비밀번호 변경 DTO
    @Data
    public static class ChangePWRequest {
        private String name;
        private String birth;
        private String newpw;
        private String impUid;
    }
}