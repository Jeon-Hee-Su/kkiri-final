package com.kkiri.mapper;

import com.kkiri.model.dto.ExpenseRequest;
import com.kkiri.model.dto.ExpenseResponse;
import com.kkiri.model.dto.ExpenseResponse.ItemResponse;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param; // 이 임포트가 필요합니다.
import java.util.List;

@Mapper
public interface ReceiptMapper {
    // 1. 메인 지출 정보 저장
    // XML의 selectKey 덕분에 실행 후 request.getExpenseId()로 ID 확인 가능
    int insertExpense(ExpenseRequest request);

    // 2. 상세 품목들 저장 (매개변수가 2개이므로 @Param 권장)
    int insertReceiptItems(@Param("items") List<ExpenseRequest.ItemRequest> items, 
                           @Param("expenseId") Integer expenseId);

    // 3. 특정 그룹의 내역 조회
    List<ExpenseResponse> selectExpensesByGroupId(Integer groupId);
    
    // 4. 특정 영수증의 품목 상세 조회
	/*
	 * List<ExpenseResponse.ItemResponse> selectExpenseByExpenseId(Integer
	 * expenseId);
	 */

	List<ItemResponse> selectItemsByExpenseId(Integer expenseId);

    // 5. 특정 transactionId에 expenseId를 직접 연결
    int linkExpenseToTransaction(@Param("transactionId") Long transactionId,
                                 @Param("expenseId") Integer expenseId);

    // 6. 영수증 삭제 관련
    int unlinkExpenseFromTransaction(@Param("expenseId") Integer expenseId);
    int deleteReceiptItems(@Param("expenseId") Integer expenseId);
    int deleteExpense(@Param("expenseId") Integer expenseId, @Param("userId") Integer userId);
}