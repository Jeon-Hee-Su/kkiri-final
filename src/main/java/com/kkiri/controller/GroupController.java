package com.kkiri.controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.kkiri.model.dto.GroupDTO.GroupDetailResponse;
import com.kkiri.model.dto.GroupDTO.GroupFillRequest;
import com.kkiri.model.dto.GroupDTO.GroupListResponseLong;
import com.kkiri.model.vo.GroupVO;
import com.kkiri.model.vo.UserVO;
import com.kkiri.model.vo.SettlementVO;
import com.kkiri.model.vo.SettlementDetailVO;
import com.kkiri.mapper.SettlementMapper;
import com.kkiri.service.FcmService;
import com.kkiri.service.GroupService;
import com.kkiri.service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/group")
@RequiredArgsConstructor // 생성자 주입
public class GroupController {

    private final GroupService groupService;
    private final UserService userService;

    @Autowired
    private SettlementMapper settlementMapper;
    private final FcmService fcmService;
    
    /**
     * 신규 모임 생성 및 전용 계좌 개설 API 
     */
    @PostMapping("/create")
    public ResponseEntity<?> createGroup(@RequestBody Map<String, Object> payload, Authentication authentication) {
        try {
            // 1. 인증 정보 체크
            if (authentication == null) {
                return ResponseEntity.status(401).body("로그인이 필요합니다.");
            }
            
            // [로그 추가] JS에서 어떤 데이터가 넘어오는지 전체 출력!
            log.info("프론트에서 넘어온 페이로드: {}", payload);

            // 2. 요청 데이터 추출
            String groupName = (String) payload.get("groupName");
            String bankCode = (String) payload.get("bankCode");
            String paymentPassword = (String) payload.get("paymentPassword"); // JS의 'pin'
            String category = (String) payload.get("category");
            
            if (paymentPassword == null) {
                log.error("비밀번호(pin)가 null입니다! 페이로드를 확인하세요.");
                // 임시 방편: paymentPassword = (String) payload.get("password"); 
            }
            
            log.info("모임 생성 요청 - 그룹명: {},카테고리: {}, 은행코드: {}, 비번전달여부: {}", 
                    groupName,category, bankCode, (paymentPassword != null));

            // 3. 유저 정보 조회 (이메일 기반)
            UserVO user = userService.findByEmail(authentication.getName());
            if (user == null) {
                return ResponseEntity.badRequest().body("유저 정보를 찾을 수 없습니다.");
            }

            // 4. 초대 코드 생성 (8자리 랜덤 영문/숫자)
            String inviteCode = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            // 5. VO 객체 조립
            GroupVO groupVO = new GroupVO();
            groupVO.setGroupName(groupName);
            groupVO.setInviteCode(inviteCode);
            groupVO.setCategory(category);

            // 6. 서비스 호출 (그룹 + 멤버 + 계좌 일괄 처리)
            Map<String, Object> result = groupService.registerNewGroupWithAccount(
            	    groupVO, 
            	    user.getUserId(), 
            	    bankCode, 
            	    paymentPassword
            );
            
            int groupId = (int) result.get("groupId");
            String realAccountNumber = (String) result.get("accountNumber");

            log.info("모임 생성 완료 - ID: {}, 진짜계좌: {}", groupId, realAccountNumber);

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "groupId", groupId,
                "inviteCode", inviteCode,
                "accountNumber", realAccountNumber, // 가짜 대신 서비스에서 온 진짜 번호를 리턴!
                "category", category
            ));

        } catch (Exception e) {
            log.error("모임 생성 중 서버 에러 발생", e);
            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", "모임 및 계좌 생성 중 오류가 발생했습니다."
            ));
        }
    }

    @GetMapping("/check-permission")
    public ResponseEntity<?> checkPermission() {
        // 실제 구현 시 권한 체크 로직 추가 가능
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "canCreate", true
        ));
    }
    
    @GetMapping("/my-groups")
    public ResponseEntity<?> getMyGroups(Authentication authentication) {
        try {
            if (authentication == null) return ResponseEntity.status(401).build();
            
            // 1. 유저 정보 가져오기 (이메일 기반)
            UserVO user = userService.findByEmail(authentication.getName());
            
            // 2. 해당 유저가 속한 그룹 리스트 조회 (정기 회비 정보 포함)
            // GroupService에 해당 유저의 그룹 리스트를 가져오는 메서드가 있어야 합니다.
            List<GroupVO> groups = groupService.findGroupsByUserId(user.getUserId());
            
            return ResponseEntity.ok(groups); // JSON 형태로 반환
        } catch (Exception e) {
            return ResponseEntity.status(500).body("그룹 정보를 불러오는 중 에러 발생");
        }
    }
    
    /**
     * 회비 채우기 API (102번 유저 고정 버전)
     * 실제 서비스 시 Principal을 사용해 동적으로 변경 예정
     */
    /**
     * 회비 채우기 API (로그인 유저 동적 처리 버전)
     */
    @PostMapping("/fill") // 최종 주소: /api/group/fill
    @ResponseBody
    public ResponseEntity<?> fillGroupMoney(@RequestBody Map<String, Object> data, Principal principal) {
        try {
            // 1. 인증 정보 확인 (JwtFilter가 SecurityContext에 저장한 정보)
            if (principal == null) {
                log.warn("인증되지 않은 사용자의 입금 시도");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                     .body(Map.of("error", "로그인이 필요합니다."));
            }

            // 2. 토큰의 주인(이메일) 추출
            String email = principal.getName(); 
            log.info("회비 채우기 시도 - 사용자 이메일: {}", email);

            // 3. 이메일을 기반으로 DB의 실제 userId(숫자 PK) 조회
            // userService에 findByEmail 메서드가 구현되어 있어야 합니다.
            UserVO user = userService.findByEmail(email);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                     .body(Map.of("error", "사용자 정보를 찾을 수 없습니다."));
            }

            int userId = user.getUserId(); // 드디어 동적인 userId 획득!

            // 4. 프론트에서 넘어온 데이터 추출
            int groupId = Integer.parseInt(data.get("groupId").toString());
            long amount = Long.parseLong(data.get("amount").toString());

            log.info("입금 실행 - 유저ID: {}, 그룹ID: {}, 금액: {}", userId, groupId, amount);

            // 5. 비즈니스 로직 실행
            groupService.fillGroupMoney(userId, groupId, amount);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "회비 채우기가 완료되었습니다!"
            ));
            
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                 .body(Map.of("error", "데이터 형식이 올바르지 않습니다."));
        } catch (Exception e) {
            log.error("입금 처리 중 서버 에러: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(Map.of("error", "서버 오류: " + e.getMessage()));
        }
    }

    @DeleteMapping("/delete/{groupId}")
    @ResponseBody
    @Transactional // 이 안에서 하나라도 터지면 전체 롤백됩니다.
    public Map<String, Object> deleteGroup(@PathVariable int groupId) {
        Map<String, Object> response = new HashMap<>();
        try {
            // 1. 잔액 조회 (Null 방지를 위해 기본값 0 처리)
            Integer currentBalance = groupService.getGroupBalance(groupId);
            int balance = (currentBalance != null) ? currentBalance : 0;
            
            log.info("그룹({}) 삭제 시도 - 현재 잔액: {}원", groupId, balance);

            // 2. 잔액이 있을 때만 정산 로직 진입
            if (balance > 0) {
                List<Integer> memberIds = groupService.getGroupMemberIds(groupId);
                int memberCount = (memberIds != null) ? memberIds.size() : 0;

                if (memberCount > 0) {
                    int perPerson = balance / memberCount;
                    int extra = balance % memberCount;

                    for (int i = 0; i < memberCount; i++) {
                        int finalAmount = (i == 0) ? (perPerson + extra) : perPerson;
                        // 중요: userService.addBalance 내부에서 에러가 나면 
                        // 전체가 롤백되므로 Mapper 유무를 꼭 확인해야 합니다!
                        userService.addBalance(memberIds.get(i), finalAmount, "모임 종료 정산");
                    }
                }
            }

            // 3. 그룹 삭제 (잔액이 0원이면 바로 여기로 점프)
            groupService.deleteGroupById(groupId);
            
            response.put("success", true);
            response.put("message", balance > 0 ? "정산 후 삭제되었습니다." : "잔액이 없어 즉시 삭제되었습니다.");

        } catch (Exception e) {
            log.error("그룹 삭제 중 치명적 오류: ", e);
            // 트랜잭션 마킹 에러를 방지하기 위해 예외를 명시적으로 던져줍니다.
            throw new RuntimeException("삭제 실패: " + e.getMessage());
        }
        return response;
    }
   

 // 1. 유저 ID로 가입된 그룹 목록 조회
    @GetMapping("/list")
    public ResponseEntity<List<GroupListResponseLong>> getGroupList(@RequestParam int userId) {
        return ResponseEntity.ok(groupService.findGroupsByUserId2(userId));
    }

    // 2. 그룹 상세 정보 조회 (잔액, 계좌번호 등)
    @GetMapping("/detail/{groupId}")
    public ResponseEntity<GroupDetailResponse> getGroupDetail(@PathVariable int groupId) {
        return ResponseEntity.ok(groupService.getGroupDetail(groupId));
    }
    
    @GetMapping("/detail/service_center")
    public String service_center() {
       return "support/service_center";
    }

    @PostMapping("/deposit") 
    public ResponseEntity<?> depositToGroup(@RequestBody GroupFillRequest request, 
                                          Principal principal) {
        try {
            if (principal == null) return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다."));

            // 서비스 호출 (DTO에서 데이터를 꺼내 전달)
            // request.groupId(), request.amount(), request.accountNumber() 사용
            groupService.transferToGroup(request); 
            
            return ResponseEntity.ok().body(Map.of("status", "success", "message", "입금 성공"));
        } catch (Exception e) {
            log.error("입금 처리 중 에러: ", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 그룹 최근 거래내역 조회 API
     * GET /api/group/{groupId}/transactions?limit=10
     */
    @GetMapping("/{groupId}/transactions")
    public ResponseEntity<?> getGroupTransactions(
            @PathVariable int groupId,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Authentication authentication) {
        try {
            if (authentication == null) return ResponseEntity.status(401).body("로그인이 필요합니다.");

            // startDate/endDate가 있으면 날짜 범위 조회 (group-history 페이지)
            if (startDate != null && endDate != null) {
                return ResponseEntity.ok(
                    groupService.getTransactionsByDateRange(groupId, startDate, endDate)
                );
            }
            // 없으면 기존 최근 N건 조회 (group-main 페이지)
            return ResponseEntity.ok(groupService.getRecentTransactions(groupId, limit));
        } catch (Exception e) {
            log.error("거래내역 조회 에러: ", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
 // GroupController.java 하단 수정

    @PostMapping("/update-rules") // ✅ 클래스 상단에 /api/group이 있으므로 /update-rules만 적습니다.
    @ResponseBody
    public ResponseEntity<?> updateFeeRules(@RequestBody Map<String, Object> rules) {
        try {
            log.info("회비 규칙 업데이트 요청: {}", rules); // 로그 추가로 확인

            Long groupId = Long.parseLong(rules.get("groupId").toString());
            int regularDay = Integer.parseInt(rules.get("regularDay").toString());
            int regularAmount = Integer.parseInt(rules.get("regularAmount").toString());
            int penaltyDay = Integer.parseInt(rules.get("penaltyDay").toString());
            int penaltyAmount = Integer.parseInt(rules.get("penaltyAmount").toString());

            // ✅ 2. 서비스 호출 주석 해제 (이제 ServiceImpl에 구현되어 있으니 작동합니다!)
            groupService.updateGroupFeeRules(groupId, regularDay, regularAmount, penaltyDay, penaltyAmount);

            return ResponseEntity.ok().body(Map.of("success", true, "message", "회비 규칙이 저장되었습니다."));
        } catch (Exception e) {
            log.error("회비 규칙 저장 중 에러: ", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "저장 중 오류 발생"));
        }
    }
    @PostMapping("/send-notification")
    @ResponseBody
    public ResponseEntity<?> sendNotification(@RequestBody Map<String, Object> data) {
        try {
            log.info("알림 전송 요청 데이터: {}", data);

            // 1. memberIds 추출
            List<Integer> memberIds = (List<Integer>) data.get("memberIds");

            // 2. groupId 추출
            int groupId = Integer.parseInt(data.get("groupId").toString());

            // 3. 메시지 / 제목 추출
            String message = (String) data.get("message");
            if (message == null || message.isEmpty()) {
                message = "모임에서 알림을 보냈습니다.";
            }

            // 4. 총 금액 추출 (JS에서 totalAmount로 전달)
            long totalAmount = data.get("totalAmount") != null
                    ? ((Number) data.get("totalAmount")).longValue() : 0L;

            // 5. 정산 제목 (message에서 추출하거나 별도 title 필드)
            String title = data.get("title") != null ? (String) data.get("title") : message;

            // 6. SETTLEMENTS 테이블에 저장
            if (totalAmount > 0 && memberIds != null && !memberIds.isEmpty()) {
                SettlementVO settlement = new SettlementVO();
                settlement.setGroupId(groupId);
                settlement.setTitle(title);
                settlement.setTotalAmount(totalAmount);
                settlementMapper.insertSettlement(settlement);

                // 7. SETTLEMENT_DETAILS: 1/N 계산
                long perPerson = totalAmount / memberIds.size();
                long remainder = totalAmount % memberIds.size();

                java.util.List<SettlementDetailVO> details = new java.util.ArrayList<>();
                for (int i = 0; i < memberIds.size(); i++) {
                    SettlementDetailVO d = new SettlementDetailVO();
                    d.setSettlementId(settlement.getSettlementId());
                    d.setUserId(memberIds.get(i));
                    d.setAmountDue(i == 0 ? perPerson + remainder : perPerson);
                    details.add(d);
                }
                settlementMapper.insertSettlementDetails(details);
            }

            // 8. 알림 발송 (기존 로직 유지)
            groupService.saveNotifications(memberIds, groupId, message);

            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("알림 저장 실패", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}