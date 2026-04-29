package com.kkiri.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AutoTransferMapper {

    /**
     * 오늘이 납부일(DUE_DAY)인 그룹의 자동이체 대상 조회
     * 조건:
     *  - GROUP_SETTINGS.DUE_DAY = 오늘 날짜
     *  - GROUP_MEMBERS.ACCOUNT_ID IS NOT NULL (연결된 개인계좌 있음)
     *  - USER_ACCOUNTS.CUSTOMER_UID IS NOT NULL (빌링키 등록된 멤버)
     *  - 이번 달 AUTO_TRANSFER 성공 내역이 없는 멤버 (중복 방지)
     *  - 개인계좌 잔액 >= 회비금액 (잔액 부족 제외)
     */
    List<Map<String, Object>> findAutoTransferTargets(@Param("today") int today);

    /**
     * 개인계좌 잔액 차감
     * WHERE BALANCE >= amount 조건으로 부족 시 0건 반환
     */
    int deductUserAccountBalance(
            @Param("accountId") int accountId,
            @Param("amount") long amount
    );

    /**
     * 그룹 계좌 잔액 증액
     */
    void increaseGroupBalance(
            @Param("groupId") int groupId,
            @Param("amount") long amount
    );

    /**
     * 자동이체 거래내역 기록
     */
    void insertAutoTransferTransaction(
            @Param("groupId") int groupId,
            @Param("userId") int userId,
            @Param("accountId") int accountId,
            @Param("amount") long amount,
            @Param("merchantUid") String merchantUid,
            @Param("status") String status,
            @Param("description") String description
    );
}