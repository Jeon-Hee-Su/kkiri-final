package com.kkiri.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.kkiri.model.vo.TransactionVO;

@Mapper
public interface PaymentMapper {

    // 모임 계좌 잔액 증액 (회비 충전용)
    int updateGroupAccountBalance(@Param("groupId") Long groupId, @Param("amount") Long amount);

    // 현장결제 - 모임 계좌 잔액 차감 (잔액 부족 시 0건 반환)
    int deductGroupAccountBalance(@Param("groupId") int groupId, @Param("amount") long amount);

    // 기존 충전 거래 내역 기록
    int insertTransaction(TransactionVO tx);

    // QR 현장결제 거래 내역 기록 → TRANSACTIONS 테이블에 INSERT
    int insertQrTransaction(
        @Param("groupId")      int    groupId,
        @Param("amount")       long   amount,
        @Param("referenceUid") String referenceUid,
        @Param("description")  String description
    );
}
