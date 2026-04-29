package com.kkiri.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.kkiri.model.dto.ExpenseRequest;

@Mapper
public interface ExpenseMapper {
    // 지출 메인 저장 (성공 시 생성된 ID를 request 객체 내 특정 필드에 담도록 할 예정)
    void insertExpense(ExpenseRequest request);

    // 품목 리스트 저장
    void insertReceiptItems(ExpenseRequest request);
}