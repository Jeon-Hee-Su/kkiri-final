package com.kkiri.service;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.kkiri.model.dto.AuthDTO;
import com.kkiri.model.dto.AuthDTO.SignUpRequest;
import com.kkiri.model.dto.AuthDTO.SocialSignUpCompletionRequest;

import com.kkiri.model.vo.SocialAccountVO;
import com.kkiri.model.vo.UserAccountVO;
import com.kkiri.model.vo.UserVO;

public interface UserService {

	// 일반 유저 자체 회원가입
    void registerUser(AuthDTO.SignUpRequest dto);
    
	// 일반 유저 로그인
	UserVO findByLoginId(String loginId);
	
    // 아이디 중복확인
    boolean checkIdDuplicate(String loginId);
	
	// 소셜 로그인/가입 로직
	UserVO processSocialLogin(UserVO user, SocialAccountVO socialaccount);
	
	// 이메일로 유저 찾기
	UserVO findByEmail(String email);
	
	// 추가: 정기 결제용 customer_uid 업데이트
    void updateCustomerUid(String userId, String customerUid);
    
    // 소셜 정보 저장
    void registerSocialAccount(SocialAccountVO socialAccount);
    
    // 이메일 중복 여부 확인
    boolean isEmailExist(String email);
	
    // 전화번호 중복 여부 확인
    boolean isPhoneExist(String phone);
    
    // ci값 중복확인
    boolean isCiExist(String ci);
    /**
     * 신규 추가: 계좌 등록 및 초기 잔액(1,000,000원) 부여 로직
     */
    void createAccount(UserAccountVO account);
    
    
    // 프로필 조회
    UserVO getProfileDetails(int user_id);
    
    // 프로필 업데이트 관련 추가
    void updateUserInfo(int userId, AuthDTO.UpdateProfileRequest dto);

    
    // 비번 변경
    boolean updatePW(String name, String birth, String encodePW);

    // 유저 소셜 회원가입
	UserVO registerSocialUser(SocialSignUpCompletionRequest dto, String email);
	
	// 유저당 계좌 리스트 찾기
	List<UserAccountVO> findAccountListByUserId(int userId);
	
	/*
	 * //계좌 잔액 표시 UserAccountVO findPrimaryAccount(int userId);
	 */
	
	void deleteAccount(int accountId);
	
	UserAccountVO findPrimaryAccountByUserId(int userId);
	
	boolean updateGroupName(int groupId, String groupName);
	UserVO findById(int userId);
	void addBalance(int userId, int amount, String description);
	void deleteGroup(int groupId);

	// 주계좌 설정 (선택 계좌 Y, 나머지 전부 N)
	void setPrimaryAccount(int userId, int accountId);
}



