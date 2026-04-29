package com.kkiri.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kkiri.model.dto.PaymentRequestDTO;
import com.kkiri.model.vo.GroupAccountVO;
import com.kkiri.model.vo.GroupVO;
import com.kkiri.model.vo.UserVO;
import com.kkiri.service.GroupService;
import com.kkiri.service.PaymentService;
import com.kkiri.service.SseService;
import com.kkiri.service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final GroupService groupService;
    private final UserService userService;
    private final SseService sseService;

    // QR 토큰 인메모리 저장소 (token -> {groupId, accountId, balSance, expiry})
    private static final ConcurrentHashMap<String, Map<String, Object>> QR_TOKEN_STORE
            = new ConcurrentHashMap<>();

    // 현재 결제 처리 중인 토큰 저장소 (동시 결제 차단용)
    private static final ConcurrentHashMap<String, Boolean> PROCESSING_TOKENS
            = new ConcurrentHashMap<>();

    // 이미 사용 완료된 토큰 저장소 (사용된 QR 안내용, 10분간 보관)
    private static final ConcurrentHashMap<String, Long> USED_TOKENS
            = new ConcurrentHashMap<>();

    private static final long QR_EXPIRE_MS   = 3  * 60 * 1000L; // 3분
    private static final long USED_RETAIN_MS = 10 * 60 * 1000L; // 사용된 토큰 10분 보관

    /**
     * 기존 PortOne 충전 API (유지)
     */
    @PostMapping("/charge")
    public ResponseEntity<?> charge(@RequestBody PaymentRequestDTO dto) {
        paymentService.processCharge(dto);
        return ResponseEntity.ok().build();
    }

    /**
     * 1. 내 그룹 목록 + 각 그룹 계좌 잔액 조회
     */
    @PostMapping("/qr/groups")
    public ResponseEntity<?> getMyGroupsWithBalance(Authentication authentication) {
        if (authentication == null) return ResponseEntity.status(401).build();

        try {
            UserVO user = userService.findByEmail(authentication.getName());
            if (user == null) return ResponseEntity.status(404).body("사용자를 찾을 수 없습니다.");

            List<GroupVO> groups = groupService.findGroupsByUserId(user.getUserId());

            List<Map<String, Object>> result = new java.util.ArrayList<>();
            for (GroupVO g : groups) {
                GroupAccountVO acct = groupService.getGroupAccountByGroupId(g.getGroupId());
                Map<String, Object> item = new HashMap<>();
                item.put("groupId", g.getGroupId());
                item.put("groupName", g.getGroupName());
                item.put("balance", acct != null ? acct.getBalance() : 0);
                item.put("accountNumber", acct != null ? acct.getAccountNumber() : "-");
                result.add(item);
            }
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("그룹 목록 조회 실패", e);
            return ResponseEntity.internalServerError().body("조회 실패: " + e.getMessage());
        }
    }

    /**
     * 2. QR 결제 토큰 발급
     */
    @PostMapping("/qr/generate")
    public ResponseEntity<?> generateQrToken(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        if (authentication == null) return ResponseEntity.status(401).build();

        try {
            int groupId = Integer.parseInt(request.get("groupId").toString());

            UserVO user = userService.findByEmail(authentication.getName());
            if (user == null) return ResponseEntity.status(404).body("사용자를 찾을 수 없습니다.");
            if (!groupService.isUserInGroup(groupId, user.getUserId())) {
                return ResponseEntity.status(403).body("해당 그룹의 멤버가 아닙니다.");
            }

            GroupAccountVO acct = groupService.getGroupAccountByGroupId(groupId);
            if (acct == null) return ResponseEntity.status(404).body("그룹 계좌가 없습니다.");

            long now = System.currentTimeMillis();

            // 만료된 토큰 정리
            QR_TOKEN_STORE.entrySet().removeIf(e -> (long) e.getValue().get("expiry") < now);

            // ✅ 만료된 사용 완료 토큰도 정리
            USED_TOKENS.entrySet().removeIf(e -> e.getValue() < now);

            String token = UUID.randomUUID().toString().replace("-", "");

            Map<String, Object> tokenData = new HashMap<>();
            tokenData.put("groupId", groupId);
            tokenData.put("accountId", acct.getAccountId());
            tokenData.put("balance", acct.getBalance());
            tokenData.put("expiry", now + QR_EXPIRE_MS);

            GroupVO group = groupService.getGroupById(groupId);
            tokenData.put("groupName", group != null ? group.getGroupName() : "그룹 #" + groupId);
            tokenData.put("issuerEmail", authentication.getName()); // QR 생성자 이메일 저장

            QR_TOKEN_STORE.put(token, tokenData);

            log.info("QR 토큰 발급 - 그룹ID: {}, 잔액: {}, 토큰앞8자: {}",
                    groupId, acct.getBalance(), token.substring(0, 8));

            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("balance", acct.getBalance());
            result.put("expiresIn", 180);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("QR 토큰 발급 실패", e);
            return ResponseEntity.internalServerError().body("토큰 발급 실패: " + e.getMessage());
        }
    }

    /**
     * 3. QR 결제 처리 (pos-scanner에서 호출)
     * ✅ 사용된 QR 안내 + 동시 결제 차단 추가
     */
    @PostMapping("/qr/pay")
    public ResponseEntity<?> processQrPayment(@RequestBody Map<String, Object> request) {
        try {
            String token = (String) request.get("token");
            long amount  = Long.parseLong(request.get("amount").toString());

            if (token == null || token.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "토큰이 없습니다."));
            }
            if (amount <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "금액이 올바르지 않습니다."));
            }

            // 1. 현재 처리 중인 토큰인지 먼저 확인 (동시 결제 차단)
            if (PROCESSING_TOKENS.containsKey(token)) {
                log.warn("동시 결제 시도 차단 - 토큰앞8자: {}", token.substring(0, 8));
                return ResponseEntity.status(409).body(Map.of(
                    "error", "결제가 처리 중입니다. 잠시 후 다시 시도해주세요.",
                    "code", "PROCESSING"
                ));
            }

            // 2. 이미 사용 완료된 토큰인지 확인
            if (USED_TOKENS.containsKey(token)) {
                log.warn("사용된 QR 재사용 시도 차단 - 토큰앞8자: {}", token.substring(0, 8));
                return ResponseEntity.status(410).body(Map.of(
                    "error", "이미 사용된 QR코드입니다.",
                    "code", "USED"
                ));
            }

            Map<String, Object> tokenData = QR_TOKEN_STORE.get(token);

            // 3 토큰 존재 확인 (발급된 적 없는 토큰)
            if (tokenData == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "유효하지 않은 QR코드입니다.",
                    "code", "INVALID"
                ));
            }

            // [4] 만료 확인
            if (System.currentTimeMillis() > (long) tokenData.get("expiry")) {
                QR_TOKEN_STORE.remove(token);
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "만료된 QR코드입니다. 고객에게 재생성을 요청하세요.",
                    "code", "EXPIRED"
                ));
            }

            // 5. 원자적으로 토큰 선점 제거 (동시에 두 요청이 와도 하나만 통과)
            boolean acquired = QR_TOKEN_STORE.remove(token, tokenData);
            if (!acquired) {
                // remove 직후 다른 요청이 먼저 선점한 경우
                if (PROCESSING_TOKENS.containsKey(token)) {
                    return ResponseEntity.status(409).body(Map.of(
                        "error", "결제가 처리 중입니다. 잠시 후 다시 시도해주세요.",
                        "code", "PROCESSING"
                    ));
                }
                return ResponseEntity.status(410).body(Map.of(
                    "error", "이미 사용된 QR코드입니다.",
                    "code", "USED"
                ));
            }

            // ✅ [6] 처리 중 표시 (다른 동시 요청 차단)
            PROCESSING_TOKENS.put(token, true);

            try {
                int groupId = (int) tokenData.get("groupId");

                GroupAccountVO acct = groupService.getGroupAccountByGroupId(groupId);
                if (acct == null) {
                    return ResponseEntity.badRequest().body(Map.of("error", "그룹 계좌를 찾을 수 없습니다."));
                }
                if (acct.getBalance() < amount) {
                    return ResponseEntity.badRequest().body(Map.of(
                        "error", "잔액이 부족합니다.",
                        "balance", acct.getBalance(),
                        "requestAmount", amount
                    ));
                }

                String groupName = (String) tokenData.getOrDefault("groupName", "그룹 #" + groupId);
                paymentService.deductGroupBalance(groupId, amount, groupName);

                GroupAccountVO updated = groupService.getGroupAccountByGroupId(groupId);
                long remaining = updated != null ? updated.getBalance() : acct.getBalance() - amount;

                log.info("QR 결제 완료 - 그룹ID: {}, 차감: {}, 잔액: {}", groupId, amount, remaining);

                // ✅ [7] 사용 완료 토큰으로 등록 (10분간 보관)
                USED_TOKENS.put(token, System.currentTimeMillis() + USED_RETAIN_MS);

                // ✅ [8] QR 생성자에게 결제 완료 SSE 이벤트 전송
                String issuerEmail = (String) tokenData.get("issuerEmail");
                if (issuerEmail != null) {
                    sseService.sendQrPaymentComplete(issuerEmail, amount, remaining);
                }

                return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "groupId", groupId,
                    "deductedAmount", amount,
                    "remainingBalance", remaining
                ));

            } finally {
                // ✅ [8] 성공/실패 무관하게 처리 중 락 반드시 해제
                PROCESSING_TOKENS.remove(token);
            }

        } catch (Exception e) {
            log.error("QR 결제 처리 실패", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "결제 처리 중 오류: " + e.getMessage()));
        }
    }
}