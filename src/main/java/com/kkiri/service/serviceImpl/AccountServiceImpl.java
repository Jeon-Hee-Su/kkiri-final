package com.kkiri.service.serviceImpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kkiri.common.AccountNumberGenerator;
import com.kkiri.mapper.AccountMapper;
import com.kkiri.mapper.BankMapper;
import com.kkiri.mapper.GroupMapper;
import com.kkiri.mapper.UserMapper;
import com.kkiri.model.vo.GroupAccountVO;
import com.kkiri.model.vo.UserAccountVO;
import com.kkiri.model.vo.UserVO;
import com.kkiri.service.AccountService;
import com.kkiri.service.NotificationService;
import com.kkiri.service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

	@Autowired
    private final UserMapper userMapper;
	
	@Autowired
    private final GroupMapper groupMapper;

	@Autowired
    private final AccountMapper accountMapper;
	
	@Autowired
    private final NotificationService notificationService;
	
	@Autowired
	private final UserService userService;
    
	@Autowired
    private final PasswordEncoder passwordEncoder; // BCrypt 암호화용
    
    @Autowired
    private BankMapper bankMapper;

    
    @Override
    public String generateAccountNumber(String bankCode) {
        // 번호만 생성, DB 저장 없음
        return AccountNumberGenerator.generate(bankCode);
    }
    /**
     * 개인 계좌 생성
     */
    @Override
    @Transactional
    public String createUserAccount(int userId, String bankCode, String paymentPassword) {
    	
        // 1. 은행 코드에 맞는 계좌번호 생성 (중복 없는 번호가 생성될때 까지)
    	String accountNumber;
        do {
            accountNumber = AccountNumberGenerator.generate(bankCode);
        } while (userMapper.existsAccountNumber(accountNumber)); // 중복이면 재생성
        log.info("개인 계좌번호 생성 - 유저ID: {}, 은행코드: {}, 계좌번호: {}", userId, bankCode, accountNumber);
        
        // 2. 기존 계좌가 없으면 주계좌(Y), 있으면 부계좌(N)
        List<UserAccountVO> existing = userMapper.findAccountListByUserId(userId);
        String isPrimary = (existing == null || existing.isEmpty()) ? "Y" : "N";

        // 3. 2차 비밀번호 BCrypt 암호화
        String encodedPin = passwordEncoder.encode(paymentPassword);
        log.info("개인계좌 2차 비밀번호 BCrypt 암호화 완료 - 유저ID: {}", userId);

        // 4. VO 조립
        UserAccountVO account = UserAccountVO.builder()
                .userId(userId)
                .bankCode(bankCode)
                .accountNumber(accountNumber)
                .balance(1_000_000)   // 테스트 지원금
                .isPrimary(isPrimary)
                .paymentPassword(encodedPin)
                .build();

        // 5. DB 저장
        userMapper.insertAccount(account);
        log.info("개인 계좌 DB 저장 완료 - 유저ID: {}, 주계좌여부: {}", userId, isPrimary);
        
        try {
            // 은행 코드에 따라 이름을 예쁘게 보여주면 더 좋습니다 (예: "신한은행")
            String bankName = bankMapper.findBankName(bankCode); 
            
            notificationService.createNotification(
                userId, 
                "[" + bankName + "] " + accountNumber + " 계좌가 성공적으로 개설되었습니다. 테스트 지원금 1,000,000원이 입금되었습니다.", 
                "ACCOUNT_CREATE", 
                userId // 딱히 이동할 상세페이지가 없다면 본인 ID를 targetId로 보냅니다.
            );
        } catch (Exception e) {
            log.error("계좌 생성 알림 발송 실패: ", e);
        }
        

        return accountNumber;
    }

    /**
     * 모임 계좌 생성
     */
    @Override
    @Transactional
    public String createGroupAccount(int groupId, String bankCode, String pin) {

        // 1. 은행 코드에 맞는 계좌번호 생성
        String accountNumber = AccountNumberGenerator.generate(bankCode);
        log.info("모임 계좌번호 생성 - 모임ID: {}, 은행코드: {}, 계좌번호: {}", groupId, bankCode, accountNumber);

        // 2. VO 조립 (pin은 BCrypt로 암호화해서 저장)
        String encodedPin = passwordEncoder.encode(pin);
        log.info("2차 비밀번호 BCrypt 암호화 완료 - 모임ID: {}", groupId);

        GroupAccountVO account = GroupAccountVO.builder()
                .groupId(groupId)
                .bankCode(bankCode)
                .accountNumber(accountNumber)
                .balance(0L)
                .paymentPassword(encodedPin)
                .build();

        // 3. DB 저장
        groupMapper.insertGroupAccount(account);
        log.info("모임 계좌 DB 저장 완료 - 모임ID: {}", groupId);
        
        try {
            // 현재 로그인한 방장의 이메일 가져오기
            String email = org.springframework.security.core.context.SecurityContextHolder
                            .getContext().getAuthentication().getName();
            UserVO user = userService.findByEmail(email);
            
            // DB에서 은행명 조회 (BankMapper 활용)
            String bankName = bankMapper.findBankName(bankCode);
            if (bankName == null) bankName = "등록된 은행";

            notificationService.createNotification(
                user.getUserId(), 
                "모임 계좌[" + bankName + " " + accountNumber + "]가 생성되었습니다. 이제 멤버들의 회비를 관리할 수 있습니다!", 
                "GROUP_ACCOUNT_CREATE", 
                groupId
            );
        } catch (Exception e) {
            log.error("모임 계좌 생성 알림 실패: ", e);
        }

        return accountNumber;
    }
    
    @Override
    @Transactional
 // AccountService.java
    public boolean checkPaymentPassword(int groupId, String inputPassword) {
        // DB에서 저장된 결제 비밀번호 조회
        String storedPassword = accountMapper.findPaymentPasswordByGroupId(groupId);
        
        if (storedPassword == null) 
        	return false;
        
        // 암호화 저장 시 (BCrypt 등 사용했다면)
        return passwordEncoder.matches(inputPassword, storedPassword);
        
    }
    
}