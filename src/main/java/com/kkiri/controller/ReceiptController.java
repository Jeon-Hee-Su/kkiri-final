package com.kkiri.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kkiri.mapper.GroupMapper;
import com.kkiri.model.dto.ExpenseRequest;
import com.kkiri.model.dto.ExpenseResponse;
import com.kkiri.model.vo.UserVO;
import com.kkiri.service.GroupService;
import com.kkiri.service.ReceiptService;
import com.kkiri.service.UserService;

/**
 * [수정] 세션 방식 → JWT + 프론트 groupId 직접 전달 방식
 * 프론트에서 선택한 groupId를 요청 데이터에 포함해서 보냅니다.
 */
@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {

    @Autowired
    private ReceiptService receiptService;

    @Autowired
    private UserService userService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private GroupMapper groupMapper;

    // 저장 API
    @PostMapping("/save")
    public ResponseEntity<?> saveReceipt(@RequestBody ExpenseRequest request, Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        try {
            // 1. JWT 인증 체크
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).body("로그인이 필요합니다.");
            }

            // 2. 유저 조회
            UserVO user = userService.findByEmail(authentication.getName());
            if (user == null) {
                return ResponseEntity.status(404).body("사용자 정보를 찾을 수 없습니다.");
            }

            // 3. 프론트에서 groupId를 직접 보내므로 유효성만 체크
            if (request.getGroupId() == null || request.getGroupId() <= 0) {
                return ResponseEntity.badRequest().body("그룹을 선택해주세요.");
            }

            // 4. 해당 그룹의 멤버인지 확인
            boolean isMember = groupService.isUserInGroup(request.getGroupId(), user.getUserId());
            if (!isMember) {
                return ResponseEntity.status(403).body("해당 그룹의 멤버가 아닙니다.");
            }

            // 5. 결제자를 현재 로그인 유저로 세팅
            request.setPaidBy(user.getUserId());

            // 6. 거래 연동 모드인 경우: TRANSACTIONS 테이블의 실제 금액과 비교
            if (request.getTransactionId() != null && request.getTransactionId() > 0) {
                Long txAmount = groupMapper.selectTransactionAmountById(request.getTransactionId());
                if (txAmount == null) {
                    return ResponseEntity.badRequest().body("유효하지 않은 거래입니다.");
                }
                if (!txAmount.equals(Long.valueOf(request.getAmount()))) {
                    return ResponseEntity.badRequest().body(
                        "결제금액(" + request.getAmount() + "원)이 실제 거래금액(" + txAmount + "원)과 다릅니다."
                    );
                }
            }

            receiptService.saveFullReceipt(request);

            response.put("status", "success");
            response.put("message", "정상적으로 저장되었습니다.");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
        	e.printStackTrace();
            System.err.println("❌ [ReceiptController] 저장 실패 원인: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("❌ [ReceiptController] 원인(Cause): " + e.getCause().getMessage());
            }
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 조회 API - groupId를 쿼리 파라미터로 받음
    @GetMapping("/list")
    public ResponseEntity<?> getHistory(
            @RequestParam(value = "groupId", required = false) Integer groupId,
            Authentication authentication) {

        // 1. JWT 인증 체크
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body("로그인이 필요합니다.");
        }

        // 2. groupId 없으면 첫 번째 그룹 자동 사용
        if (groupId == null || groupId <= 0) {
            UserVO user = userService.findByEmail(authentication.getName());
            if (user == null) return ResponseEntity.status(404).body("사용자 정보를 찾을 수 없습니다.");

            var groups = groupService.findGroupsByUserId(user.getUserId());
            if (groups == null || groups.isEmpty()) {
                return ResponseEntity.ok(List.of()); // 빈 목록 반환
            }
            groupId = groups.get(0).getGroupId();
        }

        List<ExpenseResponse> history = receiptService.getGroupHistory(groupId);
        return ResponseEntity.ok(history);
    }
    
    @GetMapping("/items")
    public ResponseEntity<?> getItems(
    		@RequestParam("expenseId") Integer expenseId,
    		Authentication authentication){
    	
    	if(authentication == null || !authentication.isAuthenticated()) {
    		return ResponseEntity.status(401).body("로그인이 필요합니다");
    	}
    	
    	return ResponseEntity.ok(receiptService.getItemsByExpenseId(expenseId));
    }

    // 영수증 삭제 API
    @DeleteMapping("/{expenseId}")
    public ResponseEntity<?> deleteReceipt(
            @PathVariable Integer expenseId,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).body("로그인이 필요합니다.");
            }

            UserVO user = userService.findByEmail(authentication.getName());
            if (user == null) {
                return ResponseEntity.status(404).body("사용자 정보를 찾을 수 없습니다.");
            }

            receiptService.deleteReceipt(expenseId, user.getUserId());

            response.put("status", "success");
            response.put("message", "영수증이 삭제되었습니다.");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}