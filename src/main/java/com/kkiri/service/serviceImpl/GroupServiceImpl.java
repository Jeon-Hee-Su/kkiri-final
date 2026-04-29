package com.kkiri.service.serviceImpl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kkiri.mapper.GroupMapper;
import com.kkiri.mapper.UserMapper;
import com.kkiri.model.dto.GroupDTO;
import com.kkiri.model.dto.GroupDTO.GroupDetailResponse;
import com.kkiri.model.dto.GroupDTO.GroupFillRequest;
import com.kkiri.model.dto.GroupDTO.GroupListResponseLong;
import com.kkiri.model.vo.GroupAccountVO;
import com.kkiri.model.vo.GroupMemberVO;
import com.kkiri.model.vo.GroupVO;
import com.kkiri.model.vo.UserAccountVO;
import com.kkiri.model.vo.UserVO;
import com.kkiri.service.AccountService;
import com.kkiri.service.FcmService;
import com.kkiri.service.GroupService; // 인터페이스가 상위 패키지에 있다면 import 필요
import com.kkiri.service.NotificationService;
import com.kkiri.service.UserService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GroupServiceImpl implements GroupService {

	@Autowired
	private GroupMapper groupMapper;

	@Autowired
	private UserMapper userMapper;

	@Autowired
	private AccountService accountService;

	@Autowired
	@Lazy
	private NotificationService notificationService;

	@Autowired
	private FcmService fcmService;

	@Autowired
	private UserService userService;

	@Override
	public List<GroupVO> findAllGroups() {
		// 매퍼에 정의된 메서드를 호출합니다.
		return groupMapper.selectAllActiveGroups();
	}

	@Override
	public List<GroupMemberVO> getMemberList(int groupId) {
		return groupMapper.getMemberList(groupId);
	}

	/**
	 * [수정 완료] 신규 모임 생성 + 방장 등록 + 모임 전용 계좌 개설
	 */
	@Override
	@Transactional
	public Map<String, Object> registerNewGroupWithAccount(GroupVO group, int userId, String bankCode, String pin) {

		// 1. GROUPS 테이블에 모임 정보 저장
		groupMapper.insertGroup(group);
		int generatedGroupId = group.getGroupId();

		// 2. 방장(HOST) 등록 - 주계좌 accountId 함께 저장 (빌링키 자동이체용)
		UserAccountVO hostPrimaryAccount = userMapper.findPrimaryAccountByUserId(userId);
		Integer hostAccountId = (hostPrimaryAccount != null) ? hostPrimaryAccount.getAccountId() : null;
		groupMapper.insertInitialHost(generatedGroupId, userId, hostAccountId);

		// 3. 모임 계좌 생성 (번호 생성 + DB 저장 AccountService에서 처리)
		String accountNumber = accountService.createGroupAccount(generatedGroupId, bankCode, pin);

		// 4. 생성된 정보 반환
		// 빌더 패턴 또는 Setter로 데이터 조립
		/*
		 * GroupAccountVO accountVO = GroupAccountVO.builder()
		 * .groupId(generatedGroupId) .bankCode(bankCode) .accountNumber(accountNumber)
		 * .balance(0L) .paymentPassword(pin) .build();
		 * 
		 * // 매퍼 호출 (VO 전달) groupMapper.insertGroupAccount(accountVO);
		 */

		notificationService.createNotification(userId, "'" + group.getGroupName() + "' 그룹이 성공적으로 생성되었습니다!", "GROUP",
				generatedGroupId);

		/*
		 * fcmService.sendPushToUser( userId, "우리끼리 - 그룹 생성", "'" + group.getGroupName()
		 * + "' 그룹이 생성되었습니다!" );
		 */

		// [핵심] 생성된 정보를 Map에 담아서 반환
		Map<String, Object> result = new HashMap<>();
		result.put("groupId", generatedGroupId);
		result.put("accountNumber", accountNumber);

		return result;
	}

	// (참고) 기존 단순 생성 로직이 필요 없다면 삭제하거나 위 메서드를 호출하게 두셔도 됩니다.
	@Override
	public int registerNewGroup(GroupVO group, int userId) {
		Map<String, Object> result = registerNewGroupWithAccount(group, userId, "000", "000000");
		return (int) result.get("groupId");
	}

	@Override
	public List<GroupVO> findGroupsByUserId(int userId) {
		// 매퍼를 통해 DB에서 목록을 가져옵니다.
		return groupMapper.findGroupsByUserId(userId);
	}

	@Override
	public GroupVO getGroupById(int groupId) {
		// Mapper를 통해 DB에서 데이터를 가져옵니다.
		return groupMapper.getGroupById(groupId);
	}

	@Override
	public Map<String, Object> getGroupDetailsByCode(String code) {
		return groupMapper.getGroupByInviteCode(code);
	}

	@Transactional(rollbackFor = Exception.class) // 에러 나면 전으로 되돌림
	@Override
	public void fillGroupMoney(int userId, int groupId, long amount) {
		// 1. 내 계좌에서 인출
		int withdrawResult = groupMapper.withdrawUserMoney(userId, amount);
		if (withdrawResult == 0) {
			throw new RuntimeException("잔액이 부족하거나 기본 계좌가 없습니다.");
		}

		// 2. 모임 계좌로 입금
		int depositResult = groupMapper.depositGroupMoney(groupId, amount);
		if (depositResult == 0) {
			throw new RuntimeException("모임 계좌 입금에 실패했습니다.");
		}

		// 3. TRANSACTIONS 테이블에 거래내역 기록 (회비채우기)
		try {
			// TARGET_ACCOUNT_ID: GROUP_ACCOUNT의 ACCOUNT_ID 조회
			com.kkiri.model.vo.GroupAccountVO groupAcc = groupMapper.getGroupAccountByGroupId(groupId);
			if (groupAcc != null) {
				com.kkiri.model.vo.TransactionVO tx = new com.kkiri.model.vo.TransactionVO();
				// SOURCE_ACCOUNT_ID: 개인 계좌 ID (없으면 null 허용)
				java.util.List<com.kkiri.model.vo.UserAccountVO> userAccounts = userMapper.findAccountListByUserId(userId);
				if (userAccounts != null && !userAccounts.isEmpty()) {
					tx.setSourceAccountId(Long.valueOf(userAccounts.get(0).getAccountId()));
				}
				tx.setTargetAccountId(Long.valueOf(groupAcc.getAccountId()));
				tx.setAmount(amount);
				tx.setTransactionType("TRANSFER");
				tx.setStatus("SUCCESS");
				tx.setPaymentReferenceUid("FILL_" + groupId + "_" + userId + "_" + System.currentTimeMillis());
				com.kkiri.model.vo.UserVO txUser = userMapper.findById(userId);
				String txNickname = (txUser != null && txUser.getNickname() != null) ? txUser.getNickname() : userId + "번 회원";
				tx.setDescription("회비입금 - " + txNickname);
				groupMapper.insertGroupTransaction(tx);
				log.info("회비채우기 거래내역 기록 완료 - userId: {}, groupId: {}, amount: {}", userId, groupId, amount);
			}
		} catch (Exception e) {
			log.error("TRANSACTIONS INSERT 실패 (회비채우기): ", e);
			// 거래내역 기록 실패가 입금 자체를 롤백시키지 않도록 catch
		}

        try {
            // (1) 돈을 보낸 본인에게 출금 완료 알림
            notificationService.createNotification(
                userId,
                "그룹 계좌로 " + amount + "원이 정상 출금되었습니다.",
                "TRANSFER_OUT", // 출금 타입
                groupId
            );
        	
            // GROUP_MEMBERS 테이블에서 해당 그룹의 HOST 유저 ID를 가져옵니다.
            int hostId = groupMapper.getHostIdByGroupId(groupId);
            
            // [수정됨] 방장에게 입금 확인 푸시 알림 전송
            if (hostId != 0 && hostId != userId) {
                // 방장에게 입금 확인 실시간 알림 (DB 저장 및 SSE)
                notificationService.createNotification(
                    hostId, 
                    "그룹에 새로운 입금이 확인되었습니다: " + amount + "원", 
                    "GROUP_DEPOSIT", 
                    groupId
                );

                // 방장에게 FCM 푸시 전송
                fcmService.sendPushToUser(
                    hostId, 
                    "우리끼리 - 입금 확인", 
                    "그룹 계좌로 " + amount + "원이 입금되었습니다."
                );
            }
        } catch (Exception e) {
            log.error("알림 발송 중 오류 발생: ", e);
        }
	}

	
	@Override
	public GroupAccountVO getGroupAccountByGroupId(int groupId) {
		// Mapper를 통해 DB에서 계좌 정보를 가져옵니다.
		return groupMapper.getGroupAccountByGroupId(groupId);
	}

	@Override
	@Transactional
	public int joinGroup(GroupDTO.JoinRequest request, int userId) {
		// 1. 초대 코드로 그룹 정보 조회
		Map<String, Object> groupData = groupMapper.getGroupByInviteCode(request.getInviteCode());

		// [수정] XML에서 as "groupId"로 주었으므로 소문자로 꺼내야 합니다.
		if (groupData == null || groupData.get("groupId") == null) {
			throw new RuntimeException("존재하지 않거나 유효하지 않은 초대 코드입니다.");
		}
		// [수정] "groupId" 키값 사용
		int groupId = Integer.parseInt(String.valueOf(groupData.get("groupId")));


		// 2. 이미 해당 모임의 멤버인지 확인
		GroupMemberVO existingMember = groupMapper.findMemberByGroupAndUser(groupId, userId);
		if (existingMember != null) {
			// ★ 이미 멤버지만 ACCOUNT_ID가 없으면 업데이트 (HOST 포함)
			if (existingMember.getAccountId() == null) {
				Integer existingAccountId = resolveAccountId(request.getAccountNumber(), userId);
				if (existingAccountId != null) {
					groupMapper.updateMemberAccountId(groupId, userId, existingAccountId);
				}
			}
			return groupId;
		}

		// 3. accountNumber → accountId 조회 (빌링키 자동이체용 1대1 매핑)
		Integer accountId = resolveAccountId(request.getAccountNumber(), userId);

		// 4. 새 멤버 등록
		GroupMemberVO newMember = new GroupMemberVO();
		newMember.setGroupId(groupId);
		newMember.setUserId(userId);
		newMember.setGroupRole("MEMBER");
		newMember.setAccountId(accountId);  // ★ 계좌 1대1 매핑

		int result = groupMapper.insertGroupMember(newMember);

		String groupName = String.valueOf(groupData.get("groupName"));

		if (result > 0) {
			// [알림 적용] 가입한 본인 알림
			fcmService.sendPushToUser(userId, "우리끼리 - 가입 완료", "'" + groupName + "' 그룹에 가입되었습니다.");

			notificationService.createNotification(userId, "'" + groupName + "' 그룹에 성공적으로 가입되었습니다.", "GROUP", groupId);

			// [알림 적용] 방장에게 알림 (방장 ID를 찾기 위해 group 객체 조회)
			int hostId = groupMapper.getHostIdByGroupId(groupId);
			fcmService.sendPushToUser(hostId, "우리끼리 - 새 멤버", "'" + groupName + "' 그룹에 새로운 멤버가 합류했습니다.");

			notificationService.createNotification(hostId, "'" + groupName + "' 그룹에 새로운 멤버가 합류했습니다!", "GROUP", groupId);
		}

		return groupId;
	}

	/**
	 * 계좌번호 → accountId 조회 헬퍼
	 * - accountNumber 전달 시: 본인 계좌 검증 후 accountId 반환
	 * - accountNumber 미전달 시: 주계좌(IS_PRIMARY=Y) accountId 반환
	 */
	private Integer resolveAccountId(String accountNumber, int userId) {
		if (accountNumber != null && !accountNumber.isBlank()) {
			Integer accountId = userMapper.findAccountIdByAccountNumberAndUserId(accountNumber, userId);
			if (accountId == null) {
				throw new RuntimeException("선택한 계좌가 존재하지 않거나 본인 계좌가 아닙니다.");
			}
			return accountId;
		} else {
			UserAccountVO primaryAccount = userMapper.findPrimaryAccountByUserId(userId);
			return (primaryAccount != null) ? primaryAccount.getAccountId() : null;
		}
	}

	public String getUserRole(int userId, int groupId) {
		// 실제로는 mapper를 통해 DB에서 GROUP_ROLE 컬럼을 가져와야 합니다.
		// 일단 테스트를 위해 159번 유저의 실제 역할인 "MEMBER"를 반환하게 만드세요.
		return "MEMBER";
	}

	@Override
	@Transactional // 🚩 DB 값을 바꾸는 작업이므로 트랜잭션 필수!
	public boolean removeMember(int groupId, int userId) {
		// 1. 매퍼를 통해 DB 업데이트 (MEMBER_STATUS -> 'KICKED')
		int result = groupMapper.kickGroupMember(groupId, userId);

		// 2. 영향받은 행(row)이 1개 이상이면 성공으로 간주
		return result > 0;
	}

	@Override
	public boolean isUserInGroup(int groupId, int userId) {
		// 매퍼에서 해당 유저의 JOINED 상태 카운트를 가져옵니다.
		int count = groupMapper.checkActiveMember(groupId, userId);
		return count > 0;
	}

	@Override
	public String getUserRoleInGroup(int groupId, int userId) {
		// 매퍼를 통해 DB의 GROUP_ROLE 컬럼 값을 가져옵니다.
		return groupMapper.getUserRole(groupId, userId);
	}

	@Override
	public int getGroupBalance(int groupId) {
		return groupMapper.getGroupBalance(groupId);
	}

	public List<GroupListResponseLong> findGroupsByUserId2(int userId) {
		return groupMapper.selectGroupsByUserId(userId);
	}

	@Override
	public GroupDetailResponse getGroupDetail(int groupId) {
		return groupMapper.selectGroupDetailById(groupId);
	}

	@Override
	@Transactional // 금융 거래이므로 원자성 보장 필수
	public void transferToGroup(GroupFillRequest request) {
		// 1. 내 개인 계좌에서 출금
		int withdrawRes = userMapper.withdraw(request.getAccountNumber(), request.getAmount());
		if (withdrawRes == 0) {
			throw new RuntimeException("출금 실패: 잔액이 부족하거나 계좌 정보가 올바르지 않습니다.");
		}

		// 2. 그룹 모임 통장에 입금
		int depositRes = groupMapper.depositToGroup(request.getGroupId(), request.getAmount());
		if (depositRes == 0) {
			throw new RuntimeException("입금 실패: 그룹 계좌 업데이트 중 오류가 발생했습니다.");
		}

		// 3. TRANSACTIONS 테이블에 거래내역 기록 (deposit API)
		try {
			com.kkiri.model.vo.GroupAccountVO groupAcc = groupMapper.getGroupAccountByGroupId(request.getGroupId());
			if (groupAcc != null) {
				com.kkiri.model.vo.TransactionVO tx = new com.kkiri.model.vo.TransactionVO();
				tx.setTargetAccountId(Long.valueOf(groupAcc.getAccountId()));
				tx.setAmount((long) request.getAmount());
				tx.setTransactionType("TRANSFER");
				tx.setStatus("SUCCESS");
				tx.setPaymentReferenceUid("DEPOSIT_" + request.getGroupId() + "_" + System.currentTimeMillis());
				String email2 = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
				com.kkiri.model.vo.UserVO depUser = userService.findByEmail(email2);
				String depNickname = (depUser != null && depUser.getNickname() != null) ? depUser.getNickname() : request.getAccountNumber();
				tx.setDescription("회비입금 - " + depNickname);
				groupMapper.insertGroupTransaction(tx);
				log.info("회비입금(deposit) 거래내역 기록 완료 - groupId: {}, amount: {}", request.getGroupId(), request.getAmount());
			}
		} catch (Exception e) {
			log.error("TRANSACTIONS INSERT 실패 (deposit): ", e);
		}

		try {
			String email = org.springframework.security.core.context.SecurityContextHolder.getContext()
					.getAuthentication().getName();

			UserVO user = userService.findByEmail(email);
			int userId = user.getUserId();

			// (1) 돈을 보낸 본인에게 출금 완료 알림
			notificationService.createNotification(userId, "그룹 계좌로 " + request.getAmount() + "원이 정상 출금되었습니다.",
					"TRANSFER_OUT", // 출금 타입
					request.getGroupId());

			// GROUP_MEMBERS 테이블에서 해당 그룹의 HOST 유저 ID를 가져옵니다.
			int hostId = groupMapper.getHostIdByGroupId(request.getGroupId());

			// [수정됨] 방장에게 입금 확인 푸시 알림 전송
			fcmService.sendPushToUser(hostId, "우리끼리 - 입금 확인", "그룹 계좌로 " + request.getAmount() + "원이 정상 입금되었습니다.");
		} catch (Exception e) {
			// 알림 발송 실패가 비즈니스 로직(입금) 자체를 취소시키지 않도록 예외 처리만 합니다.
			System.err.println("알림 발송 중 오류 발생: " + e.getMessage());
		}

	}

	@Override
	public List<Integer> getGroupMemberIds(int groupId) {
		return groupMapper.getGroupMemberIds(groupId);
	}

	@Override
	public void deleteGroupById(int groupId) {
		groupMapper.deleteGroupById(groupId);
	}

	@Override
	public void terminateGroup(int groupId) {
		// DB 상태를 'CLOSED'로 바꾸는 매퍼 메서드 호출
		groupMapper.updateGroupStatusToClosed(groupId);
	}

	@Override
	public List<com.kkiri.model.vo.TransactionVO> getRecentTransactions(int groupId, int limit) {
		return groupMapper.selectRecentTransactionsByGroupId(groupId, limit);
	}

	@Override
	public List<com.kkiri.model.vo.TransactionVO> getTransactionsByDateRange(int groupId, String startDate, String endDate) {
		return groupMapper.selectTransactionsByGroupIdAndDateRange(groupId, startDate, endDate);
	}

	public GroupVO getGroupDetailById(Long groupId) {
		// Mapper에서 ID로 단일 행을 조회하는 메서드를 호출합니다.
		return groupMapper.selectGroupDetailById(groupId);
	}

	@Override
	public boolean updateGroupInfo(int groupId, String groupName, String category) {
		// 1. DB 업데이트를 위해 Mapper 호출 (int 리턴값은 영향을 받은 행의 수)
		int result = groupMapper.updateGroupInfo(groupId, groupName, category);

		// 2. 성공하면 true, 실패하면 false 반환
		return result > 0;
	}

	@Override
	public GroupVO selectGroupDetailById(Long groupId) {
		// Mapper를 통해 DB에서 정보를 가져옵니다.
		// 만약 Mapper의 메서드 이름이 다르다면 그에 맞춰 수정하세요.
		return groupMapper.selectGroupDetailById(groupId);
	}

	@Override
	public void updateGroupFeeRules(Long groupId, int regularDay, int regularAmount, int penaltyDay,
			int penaltyAmount) {
		groupMapper.updateGroupFeeRules(groupId, regularDay, regularAmount, penaltyDay, penaltyAmount);
	}

	@Override
	public List<UserVO> findMembersByGroupId(int groupId) {
		// ⭐ Mapper의 쿼리 호출
		return groupMapper.findMembersByGroupId(groupId);
	}

	/*
	 * @Override
	 * 
	 * @Transactional public void saveNotifications(List<Integer> memberIds, int
	 * groupId, String message) { // 1. 혹시나 리스트가 비어있을 때 터지지 않게 방어만 해줍니다. if
	 * (memberIds == null || memberIds.isEmpty()) { return; }
	 * 
	 * // 2. 반복문 타입을 Integer로 맞춰서 'Type mismatch'를 해결합니다. for (Integer id :
	 * memberIds) { // 3. 여기에 실제 알림을 DB에 넣는 코드를 작성하세요. // 예:
	 * notificationMapper.insert(id, groupId, message); } }
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void saveNotifications(List<Integer> memberIds, int groupId, String message) {
		// 1. 리스트가 비어있으면 로직을 수행하지 않고 종료합니다.
		if (memberIds == null || memberIds.isEmpty()) {
			log.warn("알림 대상 멤버 리스트가 비어있습니다. groupId: {}", groupId);
			return;
		}

		// 2. 전달받은 모든 멤버 ID를 순회하며 알림을 생성합니다.
		for (Integer targetUserId : memberIds) {
			try {
				// [DB 저장 및 실시간 알림] fillGroupMoney의 로직을 그대로 사용
				// 이 코드가 실행되어야 NOTIFICATIONS 테이블에 데이터가 쌓입니다.
				notificationService.createNotification(
					targetUserId, 
					message, 
					"GROUP_ALARM", // 알림 타입 (원하시는 상수로 변경 가능)
					groupId
				);

				// [실시간 푸시 발송] fcmService를 활용해 휴대폰 팝업 알림 전송
				fcmService.sendPushToUser(
					targetUserId, 
					"우리끼리 - 그룹 알림", 
					message
				);
				
				log.info("멤버(ID: {})에게 알림 전송 및 DB 저장 성공", targetUserId);

			} catch (Exception e) {
				// 개별 알림 전송 중 에러가 발생해도 다른 멤버에게는 계속 전송되도록 try-catch를 내부에 둡니다.
				log.error("멤버(ID: {}) 알림 발송 중 오류 발생: ", targetUserId, e);
			}
		}
	}
	@Override
	public boolean isHost(int groupId, String userId) {
		// 1. DTO를 리턴하는 메서드 대신 VO를 리턴하는 메서드 호출
		// 만약 selectGroupVOById 같은 메서드가 없다면 Mapper에 새로 만들어야 합니다.
		GroupVO group = groupMapper.selectGroupVOById(groupId);

		// 2. 이제 GroupVO 타입이므로 getHostId() 사용 가능!
		return group != null && group.getUserId().equals(userId);
	}

	@Override
	public List<GroupMemberVO> getGroupMemberList(int groupId) {
		return groupMapper.selectGroupMembers(groupId); // 매퍼 호출 부분
	}
	@Override
	public List<Map<String, Object>> findExpensesByGroupId(Long groupId, String startDate, String endDate) {
	    return groupMapper.selectExpensesList(groupId, startDate, endDate);
	}

	@Override
	public List<Map<String, Object>> getExpensesList(Long groupId, String startDate, String endDate) {
	    return groupMapper.selectExpensesList(groupId, startDate, endDate);
	}
}