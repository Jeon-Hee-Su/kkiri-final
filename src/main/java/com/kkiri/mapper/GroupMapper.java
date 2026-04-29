package com.kkiri.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.http.ResponseEntity;

import com.kkiri.model.dto.AccountDTO;
import com.kkiri.model.dto.GroupDTO;
import com.kkiri.model.dto.GroupDTO.GroupDetailResponse;
import com.kkiri.model.dto.GroupDTO.GroupListResponseLong;
import com.kkiri.model.vo.GroupAccountVO;
import com.kkiri.model.vo.TransactionVO;
import org.apache.ibatis.annotations.Param;
import com.kkiri.model.vo.UserVO;
import com.kkiri.model.vo.GroupMemberVO;
import com.kkiri.model.vo.GroupVO;

@Mapper
public interface GroupMapper {
    
	
	
	List<GroupVO> selectAllActiveGroups();
    /**
     * 특정 그룹의 전체 멤버 목록 조회
     * @param groupId 조회할 그룹의 ID
     * @return 그룹에 속한 멤버(GroupMemberVO) 리스트
     */
    List<GroupMemberVO> getMemberList(int groupId);

    /**
     * 그룹 내 특정 멤버의 상세 정보 조회
     * (추방 전 확인이나 권한 체크 시 활용)
     * @param groupId 그룹 ID
     * @param userId 사용자 ID
     */
    GroupMemberVO findMemberByGroupAndUser(@Param("groupId") int groupId, @Param("userId") int userId);

    /**
     * 그룹에 새로운 멤버 추가 (초대 수락 시)
     * XML의 <insert id="insertGroupMember">와 연결됩니다.
     */
    int insertGroupMember(GroupMemberVO groupMember);

    /**
     * 그룹 멤버의 권한 변경 (예: MEMBER -> HOST)
     * @param groupId 그룹 ID
     * @param userId 사용자 ID
     * @param newRole 변경할 권한명
     */
    void updateMemberRole(@Param("groupId") int groupId, 
                          @Param("userId") int userId, 
                          @Param("newRole") String newRole);

    /**
     * 그룹 멤버 추방 또는 탈퇴
     * @param groupId 그룹 ID
     * @param userId 사용자 ID
     * @return 삭제된 행의 수
     */
    int deleteGroupMember(@Param("groupId") int groupId, @Param("userId") int userId);

    /**
     * 해당 사용자가 그룹의 방장(HOST)인지 확인
     * @return 방장이면 해당 정보 반환, 아니면 null
     */
    GroupMemberVO findHostByGroupId(int groupId);
    
    
    /**
     * 1. 신규 그룹(모임) 정보 생성
     * GROUPS 테이블에 데이터를 넣습니다.
     * @param group 생성할 그룹 정보 (이름, 초대코드 등)
     */
    int insertGroup(com.kkiri.model.vo.GroupVO group);

    /**
     * 2. 그룹 생성 시 최초 방장 등록
     * GROUP_MEMBERS 테이블에 데이터를 넣습니다.
     * @param groupId 생성된 그룹 ID
     * @param userId 방장이 될 사용자 ID
     */
    int insertInitialHost(@Param("groupId") int groupId, @Param("userId") int userId, @Param("accountId") Integer accountId);
    
    void insertGroupAccount(GroupAccountVO accountVO);
    
    List<GroupVO> findGroupsByUserId(int userId);
    
    GroupVO getGroupById(int groupId);
    
    Map<String, Object> getGroupByInviteCode(String code);
    
    // 개인 계좌 잔액 차감 (내 돈이 나감)
    int withdrawUserMoney(@Param("userId") int userId, @Param("amount") long amount);

    // 모임 계좌 잔액 증액 (모임 돈이 들어옴)
    int depositGroupMoney(@Param("groupId") int groupId, @Param("amount") long amount);
    
    GroupAccountVO getGroupAccountByGroupId(int groupId);
    
    int kickGroupMember(@Param("groupId") int groupId, @Param("userId") int userId);
    
    int checkActiveMember(@Param("groupId") int groupId, @Param("userId") int userId);
    
    /**
     * GROUP_MEMBERS 테이블에서 해당 유저의 ROLE을 조회
     */
    String getUserRole(@Param("groupId") int groupId, @Param("userId") int userId);
    
    List<Integer> getGroupMemberIds(int groupId);
    int getGroupBalance(int groupId);
    void deleteGroupById(int groupId);
    void updateGroupStatusToClosed(int groupId);

    int deleteGroupMembersByGroupId(int groupId);

 // 유저가 속한 그룹 리스트 조회
    List<GroupListResponseLong> selectGroupsByUserId(int userId);
    
    // 그룹 상세 정보 조회
    GroupDetailResponse selectGroupDetailById(int groupId);
    
    // 그룹 잔액 증액 (입금 처리)
    // 그룹 거래내역 조회
    List<TransactionVO> selectRecentTransactionsByGroupId(@Param("groupId") int groupId, @Param("limit") int limit);

    int depositToGroup(@Param("groupId") int groupId, @Param("amount") int amount);
    

    GroupVO selectGroupDetailById(Long groupId);
    int updateGroupInfo(@Param("groupId") int groupId, 
            @Param("groupName") String groupName, 
            @Param("category") String category);

    // 그룹장 userid 조회
    int getHostIdByGroupId(int groupId);
    int updateGroupFeeRules(@Param("groupId") Long groupId, 
            @Param("regularDay") int regularDay, 
            @Param("regularAmount") int regularAmount, 
            @Param("penaltyDay") int penaltyDay, 
            @Param("penaltyAmount") int penaltyAmount);
    List<UserVO> findMembersByGroupId(int groupId);
    void insertNotification(@Param("userId") Integer userId, 
            @Param("groupId") int groupId, 
            @Param("message") String message);
    
    GroupVO selectGroupVOById(int groupId);
    List<GroupMemberVO> selectGroupMembers(int groupId);
  
    // 회비채우기/입금 등 TRANSACTIONS 테이블에 거래내역 기록
    void insertGroupTransaction(TransactionVO tx);

    List<Map<String, Object>> selectExpensesList(
    	    @Param("groupId") Long groupId, 
    	    @Param("startDate") String startDate, 
    	    @Param("endDate") String endDate
    	);
    /** 날짜 범위로 그룹 거래내역 조회 (group-history 페이지용) */
    List<TransactionVO> selectTransactionsByGroupIdAndDateRange(
            @Param("groupId") int groupId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate
    );

	void updateMemberAccountId(
			@Param("groupId")int groupId,
			@Param("userId")int userId,
			@Param("accountId") int accountId
	);

    /** GROUP_MEMBERS의 ACCOUNT_ID로 연결된 계좌 정보 조회 */
    AccountDTO.AccountInfo getLinkedAccountByGroupAndUser(
            @Param("groupId") int groupId,
            @Param("userId") int userId
    );

    // 거래 ID로 실제 결제 금액 조회 (영수증 금액 검증용)
    Long selectTransactionAmountById(@Param("transactionId") Long transactionId);
}