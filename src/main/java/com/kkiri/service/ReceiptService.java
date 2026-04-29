package com.kkiri.service;

import java.io.File;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kkiri.mapper.ReceiptMapper;
import com.kkiri.model.dto.ExpenseRequest;
import com.kkiri.model.dto.ExpenseResponse;

@Service
public class ReceiptService {

    @Autowired
    private ReceiptMapper receiptMapper;

    // [수정 후] 단일화된 저장 로직
    @Transactional 
    public void saveFullReceipt(ExpenseRequest request) {
        // 1. 파일 이동 (임시 폴더 -> 실제 저장 폴더)
        moveFileToPermanent(request.getFileName());

        // 2. 메인 지출 정보 저장 
        receiptMapper.insertExpense(request);

        // 3. 영수증 상세 품목 저장
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            receiptMapper.insertReceiptItems(request.getItems(), request.getExpenseId());
        }

        // 4. 거래내역에서 넘어온 경우: 해당 transactionId에 명시적으로 연결
        if (request.getTransactionId() != null && request.getTransactionId() > 0) {
            receiptMapper.linkExpenseToTransaction(request.getTransactionId(), request.getExpenseId());
        }
    }

    // [수정 후] 그룹별 내역 조회 로직
    public List<ExpenseResponse> getGroupHistory(Integer groupId) {
        // 컨트롤러에서 전달받은 세션 기반 groupId를 매퍼의 조회 조건으로 전달
        return receiptMapper.selectExpensesByGroupId(groupId);
    }

 // 영수증 품목 상세 조회
    public List<ExpenseResponse.ItemResponse> getItemsByExpenseId(Integer expenseId) {
        return receiptMapper.selectItemsByExpenseId(expenseId);
    }
 
    private void moveFileToPermanent(String fileName) {
        if (fileName == null || fileName.isEmpty()) return;
 
        try {
            String basePath = System.getProperty("user.dir");
            File tempFile = new File(basePath + "/src/main/resources/static/uploads/temp/" + fileName);
            File permDir  = new File(basePath + "/src/main/resources/static/uploads/receipts/");
 
            // receipts 폴더가 없으면 자동 생성
            if (!permDir.exists()) {
                permDir.mkdirs();
            }
 
            if (tempFile.exists()) {
                tempFile.renameTo(new File(permDir, fileName));
            }
        } catch (Exception e) {
            // 파일 이동 실패는 로그만 남기고 저장 트랜잭션은 계속 진행
            System.err.println("[ReceiptService] 파일 이동 실패 (무시하고 저장 계속): " + e.getMessage());
        }
    }

    // 영수증 삭제 (TRANSACTIONS 연결 해제 → 품목 삭제 → EXPENSES 삭제 순서)
    @Transactional
    public void deleteReceipt(Integer expenseId, Integer userId) {
        // 1. TRANSACTIONS의 EXPENSE_ID 연결 해제
        receiptMapper.unlinkExpenseFromTransaction(expenseId);
        // 2. 품목 삭제
        receiptMapper.deleteReceiptItems(expenseId);
        // 3. EXPENSES 삭제 (본인 것만 삭제 가능)
        int deleted = receiptMapper.deleteExpense(expenseId, userId);
        if (deleted == 0) {
            throw new RuntimeException("삭제 권한이 없거나 존재하지 않는 영수증입니다.");
        }
    }
}