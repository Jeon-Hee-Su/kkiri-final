package com.kkiri.service;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;

import com.kkiri.model.dto.GroupDTO;
import com.kkiri.model.dto.GroupDTO.GroupDetailResponse;
import com.kkiri.model.dto.GroupDTO.GroupFillRequest;
import com.kkiri.model.dto.GroupDTO.GroupListResponseLong;
import com.kkiri.model.vo.TransactionVO;
import com.kkiri.model.vo.UserVO;
import com.kkiri.model.vo.GroupAccountVO;
import com.kkiri.model.vo.GroupMemberVO; // UserVO 대신 GroupMemberVO 임포트
import com.kkiri.model.vo.GroupVO;

public interface GroupService {
	
	List<GroupVO> findAllGroups();
    // 리턴 타입을 List<GroupMemberVO>로 변경
    List<GroupMemberVO> getMemberList(int groupId);
    
    /**
     * 2. 신규 모임 생성 및 방장 자동 등록
     * @param group 생성할 모임 정보 (이름, 목적, 회비 등)
     * @param userId 모임을 만드는 사용자 ID (방장이 될 유저)
     * @return 생성된 모임의 GROUP_ID
     */
    int registerNewGroup(GroupVO group, int userId);
    
    /**
     * 신규 추가: 모임 생성 + 방장 등록 + 모임 계좌 개설 (트랜잭션 처리)
     */
    Map<String, Object> registerNewGroupWithAccount(GroupVO group, int userId, String bankCode, String pin);
    
    /**
     * 특정 유저가 가입한 모임 목록 조회
     */
    List<GroupVO> findGroupsByUserId(int userId);
    
    GroupVO getGroupById(int groupId);
    
    Map<String, Object> getGroupDetailsByCode(String code);
    
    void fillGroupMoney(int userId, int groupId, long amount);
    
    GroupAccountVO getGroupAccountByGroupId(int groupId);
    
    int joinGroup(GroupDTO.JoinRequest request, int userId);
    
    boolean removeMember(int groupId, int userId);
    
    /**
     * 유저가 해당 그룹의 활성 멤버(JOINED)인지 확인
     */
    boolean isUserInGroup(int groupId, int userId);
    
    /**
     * 특정 그룹에서 유저의 역할(HOST 또는 MEMBER)을 조회
     */
    String getUserRoleInGroup(int groupId, int userId);

    void deleteGroupById(int groupId);
    void terminateGroup(int groupId);
    List<Integer> getGroupMemberIds(int groupId);
    int getGroupBalance(int groupId);

 // 유저 ID로 가입된 그룹 목록 조회
    //List<GroupDTO.GroupListResponseLong> findGroupsByUserId(Long userId);
    
    // 특정 그룹의 상세 정보(계좌, 잔액 등) 조회
    GroupDetailResponse getGroupDetail(int groupId);
    
    // 회비 채우기 (내 계좌 -> 그룹 계좌 이체)
    void transferToGroup(GroupFillRequest request);
    
    // 그룹 최근 거래내역 조회
    List<TransactionVO> getRecentTransactions(int groupId, int limit);
    List<TransactionVO> getTransactionsByDateRange(int groupId, String startDate, String endDate);

	List<GroupListResponseLong> findGroupsByUserId2(int userId);
	GroupVO selectGroupDetailById(Long groupId);
	boolean updateGroupInfo(int groupId, String groupName, String category);
	void updateGroupFeeRules(Long groupId, int regularDay, int regularAmount, int penaltyDay, int penaltyAmount);
	List<UserVO> findMembersByGroupId(int groupId);
	String getUserRole(int groupId, int userId);
	void saveNotifications(List<Integer> memberIds, int groupId, String message);
	boolean isHost(int groupId, String userId);
	
	List<GroupMemberVO> getGroupMemberList(int groupId);
	List<Map<String, Object>> findExpensesByGroupId(Long groupId, String startDate, String endDate);
	List<Map<String, Object>> getExpensesList(Long groupId, String startDate, String endDate);
	
}