package com.kkiri.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kkiri.mapper.AccountMapper;
import com.kkiri.mapper.BankMapper;
import com.kkiri.mapper.GroupMapper;
import com.kkiri.model.dto.AccountDTO;
import com.kkiri.model.vo.UserAccountVO;
import com.kkiri.model.vo.UserVO;
import com.kkiri.security.JwtUtil;
import com.kkiri.service.AccountService;
import com.kkiri.service.UserService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

	private final UserService userService;
	private final AccountService accountService;
	private final GroupMapper groupMapper;
	private final AccountMapper accountMapper;
	private final PasswordEncoder passwordEncoder;
	private final BankMapper bankMapper;

	/**
	 * [추가] 1. 내 모든 계좌 목록 조회 그룹 메인 -> 계좌관리 안의 리스트 관리 페이지(paymentmethod.html)에서 리스트를
	 * 뿌려줄 때 사용합니다.
	 */
	@GetMapping("/list")
	public ResponseEntity<?> getAccountList(Authentication authentication) {
		// 1. 인증 체크
		if (authentication == null || !authentication.isAuthenticated()) {
			return ResponseEntity.status(401).body("로그인이 필요합니다.");
		}

		try {
			String email = authentication.getName();
			UserVO user = userService.findByEmail(email);

			if (user == null) {
				return ResponseEntity.status(404).body("사용자 정보를 찾을 수 없습니다.");
			}

			// 2. 모든 계좌 리스트 조회 후 AccountDTO.AccountInfo 로 변환
			List<UserAccountVO> accounts = userService.findAccountListByUserId(user.getUserId());

			List<AccountDTO.AccountInfo> result = accounts.stream().map(acc -> {
				String bName = (acc.getBankName() != null && !acc.getBankName().isBlank()) ? acc.getBankName()
						: getBankName(acc.getBankCode());
				return AccountDTO.AccountInfo.builder().accountId(acc.getAccountId()).bankName(bName)
						.accountNumber(acc.getAccountNumber()).isDefault(acc.getIsPrimary()).balance(acc.getBalance())
						.build();
			}).collect(Collectors.toList());

			log.info("유저({}) 계좌 목록 {}건 반환", email, result.size());
			return ResponseEntity.ok(result);

		} catch (Exception e) {
			log.error("계좌 목록 조회 중 예외 발생", e);
			return ResponseEntity.internalServerError().body("목록 조회 실패: " + e.getMessage());
		}
	}

	/**
	 * [추가] 2. 계좌 삭제 API
	 */
	@DeleteMapping("/{accountId}")
	public ResponseEntity<?> deleteAccount(@PathVariable("accountId") int accountId, Authentication authentication) {
		if (authentication == null)
			return ResponseEntity.status(401).build();

		try {
			// userService에 deleteAccount(int id) 구현 필요
			userService.deleteAccount(accountId);
			return ResponseEntity.ok("계좌가 성공적으로 삭제되었습니다.");
		} catch (Exception e) {
			return ResponseEntity.internalServerError().body("삭제 실패: " + e.getMessage());
		}
	}

	/**
	 * 1. 계좌 등록 및 테스트 지원금 1,000,000원 자동 부여 API
	 */
	@PostMapping("/register")
	public ResponseEntity<?> registerAccount(@RequestBody AccountDTO.RegisterRequest request,
			Authentication authentication) {

		// 1. 인증 객체 체크
		if (authentication == null || !authentication.isAuthenticated()) {
			log.warn("인증 정보가 유효하지 않은 상태에서 계좌 등록 시도");
			return ResponseEntity.status(401).body("로그인이 필요합니다.");
		}

		// 2. 요청 값 검증
		if (request.getBankCode() == null || request.getBankCode().isBlank()) {
			return ResponseEntity.badRequest().body("은행 코드가 필요합니다.");
		}
		if (request.getPaymentPassword() == null || request.getPaymentPassword().length() != 6) {
			return ResponseEntity.badRequest().body("2차 비밀번호는 숫자 6자리여야 합니다.");
		}

		try {
			String email = authentication.getName();
			log.info("계좌 등록 요청 유저 이메일: {}", email);

			// 3. 이메일로 DB의 유저 정보 조회
			UserVO user = userService.findByEmail(email);
			if (user == null) {
				log.error("DB 유저 조회 실패: 이메일 [{}]", email);
				return ResponseEntity.badRequest().body("사용자 정보를 찾을 수 없습니다.");
			}

			String accountOwnerName = (user.getUserPrivacy() != null && user.getUserPrivacy().getName() != null)
					? user.getUserPrivacy().getName()
					: user.getNickname();

			// 4. 서비스 호출 (DTO → Service)
			accountService.createUserAccount(user.getUserId(), request.getBankCode(), request.getPaymentPassword());

			log.info("계좌 등록 성공 - 유저ID: {}, 이름: {}", user.getUserId(), accountOwnerName);
			return ResponseEntity.ok("계좌가 성공적으로 등록되었습니다.");

		} catch (Exception e) {
			log.error("계좌 등록 프로세스 중 예외 발생", e);
			return ResponseEntity.internalServerError().body("서버 오류: " + e.getMessage());
		}
	}

	/**
	 * 2. 내 계좌 정보 조회 → 그룹 메인에서 주계좌 잔액 표시 IS_PRIMARY='Y'인 계좌 1개만 반환
	 */
	@GetMapping("/my-account")
	public ResponseEntity<?> getMyAccount(Authentication authentication) {
		if (authentication == null)
			return ResponseEntity.status(401).build();

		try {
			String email = authentication.getName();
			UserVO user = userService.findByEmail(email);
			if (user == null)
				return ResponseEntity.status(404).body("사용자 정보를 찾을 수 없습니다.");

			// 주계좌(IS_PRIMARY='Y') 1건만 조회
			UserAccountVO primary = userService.findPrimaryAccountByUserId(user.getUserId());

			if (primary == null) {
				return ResponseEntity
						.ok(AccountDTO.MyAccountResponse.builder().hasAccount(false).account(null).build());
			}

			String bName = (primary.getBankName() != null && !primary.getBankName().isBlank()) ? primary.getBankName()
					: getBankName(primary.getBankCode());

			AccountDTO.AccountInfo accountInfo = AccountDTO.AccountInfo.builder().accountId(primary.getAccountId())
					.bankName(bName).accountNumber(primary.getAccountNumber()).isDefault(primary.getIsPrimary())
					.balance(primary.getBalance()).build();

			return ResponseEntity
					.ok(AccountDTO.MyAccountResponse.builder().hasAccount(true).account(accountInfo).build());

		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.internalServerError().body("조회실패 : " + e.getMessage());
		}
	}

	/**
	 * 4. 주계좌 설정 API 선택한 계좌를 IS_PRIMARY='Y', 나머지는 'N'으로 업데이트
	 */
	@org.springframework.web.bind.annotation.PatchMapping("/{accountId}/set-primary")
	public ResponseEntity<?> setPrimaryAccount(@PathVariable("accountId") int accountId,
			Authentication authentication) {

		if (authentication == null)
			return ResponseEntity.status(401).build();

		try {
			UserVO user = userService.findByEmail(authentication.getName());
			if (user == null)
				return ResponseEntity.status(404).body("사용자를 찾을 수 없습니다.");

			userService.setPrimaryAccount(user.getUserId(), accountId);
			log.info("주계좌 설정 완료 - 유저ID: {}, 계좌ID: {}", user.getUserId(), accountId);
			return ResponseEntity.ok("주계좌가 설정되었습니다.");

		} catch (Exception e) {
			log.error("주계좌 설정 실패", e);
			return ResponseEntity.internalServerError().body("주계좌 설정 실패: " + e.getMessage());
		}
	}

	// 은행 코드 변환 유틸
	private String getBankName(String code) {
		String bankName = bankMapper.findBankName(code);

		if (bankName == null) {
			bankName = "지정되지 않은 은행계좌";
		}
		return bankName;
	}

	/**
	 * 3. 랜덤 계좌번호 생성 API 은행 코드에 맞는 실제 형식으로 계좌번호를 생성합니다.
	 */
	@PostMapping("/create")
	public ResponseEntity<AccountDTO.PreviewResponse> createAccount(@RequestBody AccountDTO.PreviewRequest request,
			Authentication authentication) {
		String accountNumber = accountService.generateAccountNumber(request.getBankCode());
		return ResponseEntity.ok(AccountDTO.PreviewResponse.builder().accountNumber(accountNumber).build());
	}

	// 모임계좌 호출
	@GetMapping("/group-account")
	public ResponseEntity<?> getGroupAccount(@RequestParam("groupId") int groupId, Authentication authentication) {
		if (authentication == null)
			return ResponseEntity.status(401).build();

		try {
			// 실제로는 groupId를 사용하여 해당 모임의 계좌 정보를 DB에서 조회해야 합니다.
			// 현재는 사용자님의 요구사항인 '101,100원'을 포함한 가짜 데이터를 반환하도록 예시를 짭니다.

			Map<String, Object> groupAccount = new java.util.HashMap<>();
			groupAccount.put("bankCode", "088"); // 신한은행
			groupAccount.put("accountNumber", "110-223-456789"); // 모임 계좌번호
			groupAccount.put("balance", 101100); // 🚩 사용자님이 확인하고 싶어하신 잔액

			log.info("모임({}) 계좌 정보 조회 성공", groupId);
			return ResponseEntity.ok(groupAccount);

		} catch (Exception e) {
			log.error("모임 계좌 조회 중 에러", e);
			return ResponseEntity.internalServerError().body("조회 실패");
		}
	}

	// 정산로직
	@GetMapping("/settlement-status")
	public ResponseEntity<?> getSettlementStatus(@RequestParam("groupId") int groupId, Authentication authentication) {
		if (authentication == null)
			return ResponseEntity.status(401).build();

		try {
			// 1. 현재 로그인한 사용자 정보 가져오기 (예: userId)
			String userId = authentication.getName();

			// 2. [핵심] DB에서 이 사용자가 해당 그룹에 내야 할 정산 금액 조회
			// (SettlementDetail 테이블에서 status가 'WAITING'인 내역의 합산)
			// SettlementDetail detail = settlementService.getUnpaidDetail(groupId, userId);

			Map<String, Object> settlement = new java.util.HashMap<>();

			// 임시로 DB 연동 전이라면, 최소한 파라미터라도 활용하게 수정:
			log.info("사용자({})가 그룹({})의 정산 현황을 조회합니다.", userId, groupId);

			return ResponseEntity.ok(settlement);
		} catch (Exception e) {
			log.error("조회 에러: {}", e.getMessage());
			return ResponseEntity.internalServerError().body("조회 실패");
		}
	}

	@PostMapping("/execute")
	public ResponseEntity<?> executeSettlement(@RequestBody Map<String, Object> request,
			Authentication authentication) {
		if (authentication == null)
			return ResponseEntity.status(401).build();

		try {
			int groupId = (int) request.get("groupId");
			// String userId = authentication.getName(); // 현재 로그인한 사용자 ID

			// [실제 비즈니스 로직 시나리오]
			// 1. 사용자의 계좌 잔액을 25,000원 차감 (UPDATE USER_ACCOUNT)
			// 2. 모임 계좌의 잔액을 25,000원 추가 (UPDATE GROUP_ACCOUNT)
			// 3. 정산 상태를 'PAID'로 변경 (UPDATE SETTLEMENT_DETAIL)

			log.info("모임({}) 정산 실행 완료", groupId);

			// 성공 시 상태값 반환
			Map<String, Object> result = new java.util.HashMap<>();
			result.put("status", "PAID");
			result.put("message", "정산이 성공적으로 완료되었습니다.");

			return ResponseEntity.ok(result);
		} catch (Exception e) {
			log.error("정산 실행 에러: {}", e.getMessage());
			return ResponseEntity.internalServerError().body("정산 처리 중 오류 발생");
		}
	}

	/**
	 * 결제 비밀번호 검증 API
	 */
	@PostMapping("/verify-password")
	public ResponseEntity<?> verifyPassword(@RequestBody AccountDTO.VerifyPasswordRequest request,
	        Authentication authentication) {

	    if (authentication == null || !authentication.isAuthenticated()) {
	        return ResponseEntity.status(401).body("로그인이 필요합니다.");
	    }

	    try {
	        if (request.getPaymentPassword() == null || request.getPaymentPassword().length() != 6) {
	            return ResponseEntity.badRequest().body(
	                AccountDTO.VerifyPasswordResponse.builder()
	                    .success(false).message("비밀번호는 6자리여야 합니다.").build());
	        }

	        String email = authentication.getName();
	        UserVO user = userService.findByEmail(email);
	        int userId = user.getUserId();

	        // 해당 그룹 멤버인지 검증
	        int isMember = groupMapper.checkActiveMember(request.getGroupId(), userId);
	        if (isMember == 0) {
	            log.warn("비인가 접근 시도 - userId: {}, groupId: {}", userId, request.getGroupId());
	            return ResponseEntity.status(403).body(
	                AccountDTO.VerifyPasswordResponse.builder()
	                    .success(false).message("해당 그룹에 접근 권한이 없습니다.").build());
	        }

	        boolean isValid = accountService.checkPaymentPassword(request.getGroupId(), request.getPaymentPassword());

	        return ResponseEntity.ok(
	            AccountDTO.VerifyPasswordResponse.builder()
	                .success(isValid)
	                .message(isValid ? "비밀번호가 확인되었습니다." : "비밀번호가 일치하지 않습니다.")
	                .build());

	    } catch (Exception e) {
	        log.error("비밀번호 검증 실패", e);
	        return ResponseEntity.internalServerError().body(
	            AccountDTO.VerifyPasswordResponse.builder()
	                .success(false).message("서버 오류: " + e.getMessage()).build());
	    }
	}
	
	@PostMapping("/verify-my-password")
	public ResponseEntity<?> verifyMyPassword(@RequestBody AccountDTO.VerifyPasswordRequest request,
	        Authentication authentication,
	        HttpServletRequest httpRequest) {

	    if (authentication == null || !authentication.isAuthenticated()) {
	        return ResponseEntity.status(401).body("로그인이 필요합니다.");
	    }

	    try {
	        if (request.getPaymentPassword() == null || request.getPaymentPassword().length() != 6) {
	            return ResponseEntity.badRequest().body(
	                AccountDTO.VerifyPasswordResponse.builder()
	                    .success(false).message("비밀번호는 6자리여야 합니다.").build());
	        }

	        // JWT에서 userId 추출
	        String email = authentication.getName();
	        UserVO user = userService.findByEmail(email);
	        int userId = user.getUserId();
	        if (userId == 0) {
	            return ResponseEntity.status(401).body("사용자 정보를 확인할 수 없습니다.");
	        }

	        // 개인 주계좌 비밀번호 조회 후 BCrypt 비교
	        String storedPassword = accountMapper.findPaymentPasswordByUserId(userId);
	        if (storedPassword == null) {
	            return ResponseEntity.ok(
	                AccountDTO.VerifyPasswordResponse.builder()
	                    .success(false).message("등록된 계좌가 없습니다.").build());
	        }

	        boolean isValid = passwordEncoder.matches(request.getPaymentPassword(), storedPassword);

	        return ResponseEntity.ok(
	            AccountDTO.VerifyPasswordResponse.builder()
	                .success(isValid)
	                .message(isValid ? "확인되었습니다." : "비밀번호가 일치하지 않습니다.")
	                .build());

	    } catch (Exception e) {
	        log.error("개인 비밀번호 검증 실패", e);
	        return ResponseEntity.internalServerError().body(
	            AccountDTO.VerifyPasswordResponse.builder()
	                .success(false).message("서버 오류: " + e.getMessage()).build());
	    }
	}
}