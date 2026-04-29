package com.kkiri.mapper;

import com.kkiri.model.vo.SettlementVO;
import com.kkiri.model.vo.SettlementDetailVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SettlementMapper {

    // 정산 생성
    int insertSettlement(SettlementVO settlement);

    // 정산 상세(멤버별) 일괄 생성
    int insertSettlementDetails(@Param("details") List<SettlementDetailVO> details);

    // 그룹의 진행중인 정산 목록 조회
    List<SettlementVO> selectSettlementsByGroupId(@Param("groupId") int groupId);

    // 특정 정산의 상세 목록 조회
    List<SettlementDetailVO> selectDetailsBySettlementId(@Param("settlementId") int settlementId);

    // 내가 납부해야 할 정산 상세 목록 (알림용)
    List<SettlementDetailVO> selectMyPendingDetails(@Param("userId") int userId);

    // 정산 상세 1건 납부 완료 처리
    int updateDetailPaid(@Param("detailId") int detailId);

    // 정산 전체 완료 여부 체크 후 SETTLEMENTS 상태 업데이트
    int updateSettlementIfCompleted(@Param("settlementId") int settlementId);

    // 정산 삭제 (상세 먼저)
    int deleteDetailsBySettlementId(@Param("settlementId") int settlementId);
    int deleteSettlement(@Param("settlementId") int settlementId,
                         @Param("groupId") int groupId);
}