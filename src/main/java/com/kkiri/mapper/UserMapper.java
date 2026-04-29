package com.kkiri.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.kkiri.model.dto.AuthDTO;

import com.kkiri.model.vo.SocialAccountVO;
import com.kkiri.model.vo.UserAccountVO;
import com.kkiri.model.vo.UserPrivacyVO;
import com.kkiri.model.vo.UserVO;

@Mapper
public interface UserMapper {
	
	// 이메일로 유저 찾기 (로그인 및 중복 가입 체크) 
	UserVO findByEmail(String email);
	
	// 아이디 중복 체크
	UserVO findByLoginId(String loginId);
	
	// 아이디 중복 체크
	int checkIdDuplicate(String loginId);
	
	// CI 중복 체크
	UserVO findByCi(String ci);
	
	// 전화번호 중복 확인 
	UserVO findByPhone(String phone);
	
	// 새로운 유저 가입
	int insertUser(UserVO user);
	
	// 소셜 계정 연동 정보 저장
	int insertSocialAccount(SocialAccountVO socialAccount);
	
	// 특정 사용자의 특정 소셜 연동 여부 확인
	SocialAccountVO findSocialAccount(int userId, String provider);
	
	// 유저 id로 유저 정보 조회
	UserVO findById(int userId);
	
	void updateCustomerUid(@Param("userId") String userId, @Param("customerUid") String customerUid);
	
	// XML의 <insert id="insertAccount">와 연결됩니다.
	void insertAccount(UserAccountVO account);
	
	/*
	 * // 계좌 잔액 표시 
	 * List<UserAccountVO> findAccountListByUserId(int userId);
	 */
	
	//프로필 상세 조회
	UserVO getProfileByUserId(@Param("userId") int userId);
	
	// users의 닉 수정
	int updateUserNickname(@Param("userId") int userId, @Param("nickname") String nickname);
	
	// userPro 수정
	int updateUserPrivacy(
		@Param("userId") int userId,
		@Param("name") String name,
		@Param("phone") String phone,
		@Param("birth") String birth
	);
	
	int updatePW(@Param("name") String name, @Param("birth") String birth, @Param("encodePW") String encodePW);

	void insertUserPrivacy(UserPrivacyVO privacy);
	
	List<UserAccountVO> findAccountListByUserId(int userId);
	
	UserAccountVO findPrimaryAccountByUserId(int userId);
	
	void deleteAccount(int accountId);
	void deleteGroupMembers(@Param("groupId") int groupId);
	int deleteGroup(@Param("groupId") int groupId);
	
	
	int updateGroupName(@Param("groupId") int groupId, @Param("groupName") String groupName);
	

	int updateBalance(@Param("userId") int userId, @Param("amount") int amount);
	void updateBalance(@Param("userId") Integer userId, @Param("amount") Long amount);
	

	// 개인 계좌 잔액 차감 (출금)
    // 리턴타입을 int로 두어 업데이트 성공 여부(1:성공, 0:잔액부족 등)를 확인합니다.
    int withdraw(@Param("accountNumber") String accountNumber, @Param("amount") int amount);

    // 개인 계좌 잔액 증액 (필요 시 사용)
    int deposit(@Param("accountNumber") String accountNumber, @Param("amount") int amount);

    // 주계좌 설정: 해당 유저의 모든 계좌 IS_PRIMARY → 'N'
    void resetAllPrimary(@Param("userId") int userId);

    // 주계좌 설정: 선택한 계좌만 IS_PRIMARY → 'Y' (userId 이중 검증 포함)
    void setPrimaryAccount(@Param("userId") int userId, @Param("accountId") int accountId);

    //중복없는 계좌 번호 생성
	boolean existsAccountNumber(String accountNumber);

    // ★ 추가: 계좌번호 + userId로 accountId 조회 (보안: 본인 계좌인지 확인)
    Integer findAccountIdByAccountNumberAndUserId(
        @Param("accountNumber") String accountNumber,
        @Param("userId") int userId
    );

}