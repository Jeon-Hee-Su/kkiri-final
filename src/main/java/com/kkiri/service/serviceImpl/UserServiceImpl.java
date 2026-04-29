package com.kkiri.service.serviceImpl;

import java.util.Collections;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kkiri.mapper.UserMapper;
import com.kkiri.model.dto.AuthDTO;
import com.kkiri.model.vo.SocialAccountVO;
import com.kkiri.model.vo.UserAccountVO;
import com.kkiri.model.vo.UserPrivacyVO;
import com.kkiri.model.vo.UserVO;
import com.kkiri.service.NotificationService;
import com.kkiri.service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j // 로그 어노테이션 추가
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService{
	
	private final UserMapper userMapper;
	private final NotificationService notificationService;
	private final PasswordEncoder passwordEncoder;
	
	@Override
	@Transactional
	public void registerUser(AuthDTO.SignUpRequest dto) {
	    // 1. DTO -> UserVO로 변환 및 INSERT
	    UserVO user = dto.toUserVO(passwordEncoder);
	    user.setIsVerified("Y");
	    userMapper.insertUser(user); // 이 시점에 user 객체 안에 userId가 생성됨

	    // 2. DTO -> UserPrivacyVO로 변환 (방금 생성된 userId를 넘겨줌) 및 INSERT
	    UserPrivacyVO privacy = dto.toUserPrivacyVO(user.getUserId());
	    userMapper.insertUserPrivacy(privacy);
	}
	@Override
	@Transactional
	public UserVO registerSocialUser(AuthDTO.SocialSignUpCompletionRequest dto, String email) {
	    
	    // 1. DTO가 스스로 UserVO로 변신! -> 그리고 DB에 저장
	    UserVO user = dto.toUserVO(email);
	    user.setIsVerified("Y");
	    userMapper.insertUser(user); // 실행 후 user 안에 userId가 생김

	    // 2. DTO가 스스로 UserPrivacyVO로 변신! -> 그리고 DB에 저장
	    UserPrivacyVO privacy = dto.toUserPrivacyVO(user.getUserId());
	    userMapper.insertUserPrivacy(privacy);

	    // 컨트롤러에게 방금 만든 유저 정보 전달
	    return user; 
	}
	
	
	
	@Override
	@Transactional
	public UserVO processSocialLogin(UserVO user, SocialAccountVO socialAccount) {
	    // 1. 이메일로 기존에 가입된 유저가 있는지 확인
	    UserVO existingUser = userMapper.findByEmail(user.getEmail());

	    if (existingUser != null) {
	        // (기존 유저 로직 생략...)
	        SocialAccountVO linkedAccount = userMapper.findSocialAccount(existingUser.getUserId(), socialAccount.getProvider());
	        if (linkedAccount == null) {
	            socialAccount.setUserId(existingUser.getUserId());
	            userMapper.insertSocialAccount(socialAccount);
	        }
	        return userMapper.findById(existingUser.getUserId());
	    } else {
	        // 4. 아예 새로운 유저라면 USERS 테이블에 먼저 인서트
	       
	        // 소셜에서 받은 이메일을 DB의 필수값인 loginId로 설정합니다.
	        user.setLoginId(user.getEmail()); 
	        
	        // 이제 insertUser를 호출하면 login_id 컬럼에 이메일 값이 들어갑니다.
	        userMapper.insertUser(user); 
	        
	        // 5. 생성된 userId를 소셜 계정 정보에 세팅하고 인서트
	        socialAccount.setUserId(user.getUserId());
	        userMapper.insertSocialAccount(socialAccount);
	        return user;
	    }
	}

    @Override
    public UserVO findByEmail(String email) {
        return userMapper.findByEmail(email);
    }
    
   
    
    // UserServiceImpl.java 내부에 추가
    @Override
    public boolean checkIdDuplicate(String loginId) {
        // 1. DB에서 아이디로 유저를 찾아봅니다. (이미 만들어두신 메서드 재활용!)
    	int count = userMapper.checkIdDuplicate(loginId);
        
        // 2. 유저가 null이면(없으면) true 반환 -> 사용 가능!
        // 유저가 있으면 false 반환 -> 중복됨!
    	return count == 0;
    }
    
    @Override
    public UserVO findByLoginId(String loginId) {
    	return userMapper.findByLoginId(loginId);
    }
    
    @Override
    @Transactional
    public void updateCustomerUid(String userId, String customerUid) {
        log.info("유저({})의 빌링키 업데이트 시도: {}", userId, customerUid);
        userMapper.updateCustomerUid(userId, customerUid);
    }
    @Override
    @Transactional
    public void createAccount(UserAccountVO account) {
        // 1. 로그를 남겨서 데이터가 들어오는지 확인 (디버깅용)
        log.info("DB 계좌 생성 요청 - 유저: {}, 계좌번호: {}", account.getUserId(), account.getAccountNumber());
        
        // 2. Mapper를 통해 USERS_ACCOUNTS 테이블에 Insert 실행
        userMapper.insertAccount(account); 
    }
    
    @Override
    @Transactional
    public void registerSocialAccount(SocialAccountVO socialAccount) {
        log.info("소셜 계정 정보 저장 시도 - 유저ID: {}, 제공자: {}", 
                 socialAccount.getUserId(), socialAccount.getProvider());
        
        // Mapper에 이미 insertSocialAccount가 있다면 그대로 호출하면 됩니다.
        userMapper.insertSocialAccount(socialAccount);
    }
    
    @Override
    @Transactional
    public boolean isEmailExist(String email) {
        return userMapper.findByEmail(email) != null;
    }
    
    @Override
    @Transactional
    public boolean isPhoneExist(String phone) {
    	return userMapper.findByPhone(phone) != null;
    }
    @Override
    @Transactional(readOnly = true)
    public boolean isCiExist(String ci) {
        return userMapper.findByCi(ci) != null;
    }
    
	/*
	 * //계좌 잔액표시
	 * 
	 * @Override public UserAccountVO findPrimaryAccount(int userId) { return
	 * userMapper.findAccountListByUserId(userId); }
	 */
    
    //프로필 조회
    @Override
    @Transactional(readOnly = true)
    public UserVO getProfileDetails(int userId) {
    	return userMapper.getProfileByUserId(userId);
    }
    // 프로필 업뎃
    @Override
    @Transactional
    public void updateUserInfo(int userId,  AuthDTO.UpdateProfileRequest dto) {
    	// 닉 업뎃
    	userMapper.updateUserNickname(userId, dto.getNickname());
    	
    	userMapper.updateUserPrivacy(
    			userId,
    			dto.getName(),
    			dto.getPhone(),
    			dto.getBirth()
    	);
    }
    
    @Override
    @Transactional
    public boolean updatePW(String name, String birth, String encodePW) {
    	int result = userMapper.updatePW(name, birth, encodePW);
    	
    	return result > 0;
    }
    


    // 2. 계좌 삭제 (컴파일 에러 해결용)
    @Override
    @Transactional
    public void deleteAccount(int accountId) {
        log.info("계좌 삭제 요청 - ID: {}", accountId);
        
        try {
            // 1. 현재 로그인한 유저의 이메일로 userId 찾기
            String email = org.springframework.security.core.context.SecurityContextHolder
                            .getContext().getAuthentication().getName();
            UserVO user = userMapper.findByEmail(email);
            int userId = user.getUserId();

            // 2. 계좌 삭제 실행
            userMapper.deleteAccount(accountId);

            // 3. [알림 추가] 심플한 메시지로 실시간 알림 발송
            notificationService.createNotification(
                userId, 
                "계좌(ID: " + accountId + ")가 삭제되었습니다.", 
                "ACCOUNT_DELETE", 
                accountId // targetId로 삭제된 계좌 ID 전달
            );
            
        } catch (Exception e) {
            log.error("계좌 삭제 알림 발송 실패: ", e);
            // 알림이 실패해도 실제 삭제는 진행되어야 합니다.
            userMapper.deleteAccount(accountId);
        }
    }
    // 1. 특정 유저의 모든 계좌 리스트 조회 (컴파일 에러 해결용)
    @Override
    @Transactional(readOnly = true)
    public List<UserAccountVO> findAccountListByUserId(int userId) {
        log.info("유저({})의 주 계좌 정보 조회", userId);
        
        // 1. 유저의 계좌 리스트를 가져옵니다.
        List<UserAccountVO> accounts = userMapper.findAccountListByUserId(userId);
        
        // 2. 리스트가 비어있지 않다면 첫 번째 계좌(또는 주 계좌 로직)를 반환합니다.
        if (accounts != null) {
            return accounts; // 보통 첫 번째 계좌를 주 계좌로 봅니다.
        }
        return Collections.emptyList();
    }
    
    @Override
    @Transactional(readOnly = true)
    public UserAccountVO findPrimaryAccountByUserId(int userId) {
        log.info("유저({})의 전체 계좌 목록 조회", userId);
        return userMapper.findPrimaryAccountByUserId(userId);
    }
    
    @Override
    @Transactional // ✅ DB 수정 작업은 트랜잭션 처리가 필수!
    public boolean updateGroupName(int groupId, String groupName) {
        return userMapper.updateGroupName(groupId, groupName) > 0;
    }
    
   

    @Override
    public UserVO findById(int userId) {
        // 매퍼를 호출하여 유저 정보를 가져옵니다.
        return userMapper.findById(userId);
    }
    
    @Override
    @Transactional
    public void addBalance(int userId, int amount, String description) {
        log.info("사용자({}) 잔액 충전 요청: {}원 (사유: {})", userId, amount, description);
        
        // 1. 유저의 잔액을 업데이트하는 매퍼 호출
        // (주의: userMapper에 updateBalance 메서드가 정의되어 있어야 합니다)
        int result = userMapper.updateBalance(userId, amount);
        
        if (result > 0) {
            log.info("사용자({}) 잔액 충전 성공", userId);
            // 필요하다면 여기서 거래 내역(Transaction Log) 테이블에 insert하는 로직을 추가하세요.
        } else {
            log.error("사용자({}) 잔액 충전 실패", userId);
            throw new RuntimeException("잔액 충전에 실패했습니다.");
        }
    }
    
    @Override
    @Transactional
    public void deleteGroup(int groupId) {
        log.info("그룹 삭제 요청 - ID: {}", groupId);
        userMapper.deleteGroupMembers(groupId); 
        userMapper.deleteGroup(groupId);
    }

    @Override
    @Transactional
    public void setPrimaryAccount(int userId, int accountId) {
        log.info("주계좌 설정 - 유저ID: {}, 계좌ID: {}", userId, accountId);
        // 1. 해당 유저의 모든 계좌 IS_PRIMARY → 'N'
        userMapper.resetAllPrimary(userId);
        // 2. 선택한 계좌만 IS_PRIMARY → 'Y'
        userMapper.setPrimaryAccount(userId, accountId);
    }
}
