package com.kkiri.controller;

import com.kkiri.mapper.SettlementMapper;
import com.kkiri.model.vo.GroupMemberVO;
import com.kkiri.model.vo.SettlementDetailVO;
import com.kkiri.model.vo.SettlementVO;
import com.kkiri.model.vo.UserVO;
import com.kkiri.service.GroupService;
import com.kkiri.service.NotificationService;
import com.kkiri.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settlement")
public class SettlementController {

    @Autowired private SettlementMapper settlementMapper;
    @Autowired private GroupService     groupService;
    @Autowired private UserService      userService;
    @Autowired private NotificationService notificationService;

    // ─────────────────────────────────────────────
    // [API] 그룹 정산 목록 조회
    // ─────────────────────────────────────────────
    @GetMapping("/list")
    @ResponseBody
    public ResponseEntity<?> getList(@RequestParam int groupId) {
        List<SettlementVO> list = settlementMapper.selectSettlementsByGroupId(groupId);
        // 각 정산의 상세 목록도 같이 조회
        for (SettlementVO s : list) {
            s.setDetails(settlementMapper.selectDetailsBySettlementId(s.getSettlementId()));
        }
        return ResponseEntity.ok(list);
    }

    // ─────────────────────────────────────────────
    // [API] 내 미납 정산 조회 (알림 배지용)
    // ─────────────────────────────────────────────
    @GetMapping("/my-pending")
    @ResponseBody
    public ResponseEntity<?> myPending(Authentication authentication) {
        if (authentication == null) return ResponseEntity.status(401).build();
        UserVO user = userService.findByEmail(authentication.getName());
        if (user == null) return ResponseEntity.status(404).build();
        List<SettlementDetailVO> list = settlementMapper.selectMyPendingDetails(user.getUserId());
        return ResponseEntity.ok(list);
    }

    // ─────────────────────────────────────────────
    // [API] 정산 생성
    //  body: { groupId, title, totalAmount, memberIds: [1,2,3] }
    // ─────────────────────────────────────────────
    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body,
                                    Authentication authentication) {
        Map<String, Object> res = new HashMap<>();
        try {
            if (authentication == null) return ResponseEntity.status(401).build();
            UserVO user = userService.findByEmail(authentication.getName());

            int groupId     = (int) body.get("groupId");
            String title    = (String) body.get("title");
            long total      = ((Number) body.get("totalAmount")).longValue();
            @SuppressWarnings("unchecked")
            List<Integer> memberIds = (List<Integer>) body.get("memberIds");

            if (memberIds == null || memberIds.isEmpty()) {
                return ResponseEntity.badRequest().body("멤버를 선택해주세요.");
            }

            // 1. SETTLEMENTS INSERT
            SettlementVO s = new SettlementVO();
            s.setGroupId(groupId);
            s.setTitle(title);
            s.setTotalAmount(total);
            settlementMapper.insertSettlement(s);

            // 2. 인원수로 1/N 계산
            long perPerson = total / memberIds.size();
            long remainder = total % memberIds.size();

            List<SettlementDetailVO> details = new java.util.ArrayList<>();
            for (int i = 0; i < memberIds.size(); i++) {
                SettlementDetailVO d = new SettlementDetailVO();
                d.setSettlementId(s.getSettlementId());
                d.setUserId(memberIds.get(i));
                // 나머지는 첫 번째 멤버에게
                d.setAmountDue(i == 0 ? perPerson + remainder : perPerson);
                details.add(d);
            }
            settlementMapper.insertSettlementDetails(details);

            // 3. 각 멤버에게 알림 발송
            for (Integer memberId : memberIds) {
                String msg = String.format("[%s] 정산 요청이 왔습니다. %,d원을 납부해주세요.",
                        title, (memberId.equals(memberIds.get(0)) ? perPerson + remainder : perPerson));
                notificationService.createNotification(memberId, msg, "SETTLEMENT", groupId);
            }

            res.put("status", "success");
            res.put("settlementId", s.getSettlementId());
            return ResponseEntity.ok(res);

        } catch (Exception e) {
            e.printStackTrace();
            res.put("status", "error");
            res.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(res);
        }
    }

    // ─────────────────────────────────────────────
    // [API] 납부 완료 처리
    //  body: { settlementId: N, groupId: N }
    // ─────────────────────────────────────────────
    @PostMapping("/pay/{detailId}")
    @ResponseBody
    public ResponseEntity<?> pay(@PathVariable int detailId,
                                 @RequestBody Map<String, Object> body,
                                 Authentication authentication) {
        Map<String, Object> res = new HashMap<>();
        try {
            if (authentication == null) return ResponseEntity.status(401).build();

            UserVO user = userService.findByEmail(authentication.getName());
            if (user == null) return ResponseEntity.status(404).body("사용자를 찾을 수 없습니다.");

            int settlementId = ((Number) body.get("settlementId")).intValue();
            int groupId      = ((Number) body.get("groupId")).intValue();

            // 1. 해당 상세 조회 → 금액 확인
            List<SettlementDetailVO> details = settlementMapper.selectDetailsBySettlementId(settlementId);
            SettlementDetailVO myDetail = details.stream()
                    .filter(d -> d.getDetailId() == detailId)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("정산 상세를 찾을 수 없습니다."));

            if ("PAID".equals(myDetail.getStatus())) {
                return ResponseEntity.badRequest().body("이미 납부 완료된 정산입니다.");
            }

            long amount = myDetail.getAmountDue();

            // 2. 내 계좌 → 그룹 계좌 실제 이체 (잔액 부족 시 예외 발생)
            groupService.fillGroupMoney(user.getUserId(), groupId, amount);

            // 3. 정산 상세 납부 완료 처리
            settlementMapper.updateDetailPaid(detailId);

            // 4. 전원 납부 완료 시 정산 COMPLETED 처리
            settlementMapper.updateSettlementIfCompleted(settlementId);

            res.put("status", "success");
            res.put("amount", amount);
            return ResponseEntity.ok(res);

        } catch (RuntimeException e) {
            // fillGroupMoney에서 "잔액이 부족하거나 기본 계좌가 없습니다." 등 던짐
            res.put("status", "error");
            res.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(res);
        } catch (Exception e) {
            res.put("status", "error");
            res.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(res);
        }
    }

    // ─────────────────────────────────────────────
    // [API] 정산 삭제 (HOST만)
    // ─────────────────────────────────────────────
    @DeleteMapping("/{settlementId}")
    @ResponseBody
    public ResponseEntity<?> delete(@PathVariable int settlementId,
                                    @RequestParam int groupId,
                                    Authentication authentication) {
        Map<String, Object> res = new HashMap<>();
        try {
            if (authentication == null) return ResponseEntity.status(401).build();
            settlementMapper.deleteDetailsBySettlementId(settlementId);
            settlementMapper.deleteSettlement(settlementId, groupId);
            res.put("status", "success");
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            res.put("status", "error");
            res.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(res);
        }
    }

    // ─────────────────────────────────────────────
    // [API] 그룹 멤버 목록 (정산 생성 시 멤버 선택용)
    // ─────────────────────────────────────────────
    @GetMapping("/members")
    @ResponseBody
    public ResponseEntity<?> members(@RequestParam int groupId) {
        List<GroupMemberVO> members = groupService.getMemberList(groupId);
        return ResponseEntity.ok(members);
    }

}