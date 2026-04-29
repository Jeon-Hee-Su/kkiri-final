package com.kkiri.controller; // 패키지 경로는 프로젝트 설정에 맞춰주세요.

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
import org.springframework.web.bind.annotation.RestController;

import com.kkiri.service.GroupService;
import com.kkiri.service.UserService;

import jakarta.servlet.http.HttpSession;

import com.kkiri.model.dto.GroupDTO;
import com.kkiri.mapper.GroupMapper;
import com.kkiri.model.vo.GroupMemberVO; // 새로 만든 VO 임포트
import com.kkiri.model.vo.UserVO;

import com.kkiri.model.dto.AccountDTO;
import com.kkiri.mapper.BankMapper;

@RestController
@RequestMapping("/api/group")
public class GroupMemberController {

    @Autowired
    private GroupService groupService;
    
    @Autowired
    private UserService userService;

    @Autowired
    private GroupMapper groupMapper;

    @Autowired
    private BankMapper bankMapper;

    /**
     * 특정 그룹의 멤버 리스트를 조회합니다.
     * @param groupId URL 경로에서 받아온 그룹 ID
     * @return GroupMemberVO 리스트를 포함한 응답 객체
     */
    @GetMapping("/{groupId}/members")
    public ResponseEntity<?> getMembers(
            @PathVariable("groupId") int groupId,
            Authentication authentication) {
        
        if (authentication == null) {
            return ResponseEntity.status(401).body("로그인이 필요합니다.");
        }

        try {
            String email = authentication.getName();
            UserVO user = userService.findByEmail(email);
            
            // 1. 멤버 여부 체크 (기존 로직 유지)
            boolean isActiveMember = groupService.isUserInGroup(groupId, user.getUserId());
            if (!isActiveMember) {
                return ResponseEntity.status(403).body("해당 그룹의 멤버만 접근할 수 있습니다.");
            }

            // 2. 🚩 [핵심 추가] 현재 유저가 이 그룹의 'HOST'인지 DB 기준으로 확인
            // (서비스에서 유저의 Role을 가져오는 메서드가 있다고 가정)
            String myRole = groupService.getUserRoleInGroup(groupId, user.getUserId());
            boolean amIHost = "HOST".equals(myRole);

            // 3. 리스트 조회 후 모든 객체에 내 권한 상태를 주입
            List<GroupMemberVO> members = groupService.getMemberList(groupId);
            for (GroupMemberVO member : members) {
                member.setHostUser(amIHost); // 🚩 "너 방장 맞음" 혹은 "아님"을 세팅
            }

            return ResponseEntity.ok(members);

        } catch (Exception e) {
            return ResponseEntity.status(500).body("오류 발생: " + e.getMessage());
        }
    }
    @PostMapping("/join")
    public ResponseEntity<GroupDTO.JoinResponse> joinGroup(
            @RequestBody GroupDTO.JoinRequest request, 
            Authentication authentication) { // Spring Security가 인증 객체를 자동으로 주입합니다.
        
        try {
            // 1. 하드코딩된 158 대신, 인증된 토큰에서 이메일을 추출합니다.
            if (authentication == null) {
                return ResponseEntity.status(401).body(
                    GroupDTO.JoinResponse.builder().message("로그인이 필요합니다.").build()
                );
            }
            
            String email = authentication.getName(); // 토큰에 담긴 유저 이메일
            
            // 2. 이메일을 사용해 DB에서 실제 유저 정보를 가져옵니다.
            // userService에 findByEmail 메서드가 있다고 가정합니다.
            UserVO user = userService.findByEmail(email); 
            
            if (user == null) {
                throw new RuntimeException("존재하지 않는 사용자입니다.");
            }

            int userId = user.getUserId(); // DB에 저장된 실제 PK 값 (예: 157, 173 등)

            // 3. 실제 유저 ID를 사용하여 모임 가입 처리
            int groupId = groupService.joinGroup(request, userId);

            return ResponseEntity.ok(GroupDTO.JoinResponse.builder()
                    .groupId(groupId)
                    .message("🎉 모임 가입이 완료되었습니다!")
                    .build());

        } catch (Exception e) {
            e.printStackTrace(); // 서버 콘솔에서 상세 에러 확인용
            return ResponseEntity.badRequest().body(
                GroupDTO.JoinResponse.builder().message(e.getMessage()).build()
            );
        }
    }
    
    
    /**
     * 특정 그룹에서 멤버를 제외(추방)합니다.
     * @param groupId 그룹 ID
     * @param userId 제외할 유저 ID
     */
    /**
     * 방장 개인계좌 연결 - GROUP_MEMBERS의 ACCOUNT_ID 업데이트
     */
    @PostMapping("/host-account")
    public ResponseEntity<?> updateHostAccount(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        if (authentication == null) {
            return ResponseEntity.status(401).body("로그인이 필요합니다.");
        }

        try {
            int groupId   = (int) request.get("groupId");
            int accountId = (int) request.get("accountId");

            String email = authentication.getName();
            UserVO user  = userService.findByEmail(email);

            // 본인이 해당 그룹의 HOST인지 검증
            GroupMemberVO member = groupMapper.findMemberByGroupAndUser(groupId, user.getUserId());
            if (member == null || !"HOST".equals(member.getGroupRole())) {
                return ResponseEntity.status(403).body("방장만 계좌를 연결할 수 있습니다.");
            }

            groupMapper.updateMemberAccountId(groupId, user.getUserId(), accountId);
            return ResponseEntity.ok("방장 계좌가 연결되었습니다.");

        } catch (Exception e) {
            return ResponseEntity.status(500).body("서버 오류: " + e.getMessage());
        }
    }

    /**
     * 현재 그룹에서 로그인 유저의 연결 계좌 조회
     * GET /api/group/{groupId}/my-linked-account
     */
    @GetMapping("/{groupId}/my-linked-account")
    public ResponseEntity<?> getLinkedAccount(
            @PathVariable("groupId") int groupId,
            Authentication authentication) {

        if (authentication == null) {
            return ResponseEntity.status(401).body("로그인이 필요합니다.");
        }

        try {
            String email = authentication.getName();
            UserVO user = userService.findByEmail(email);

            AccountDTO.AccountInfo account =
                    groupMapper.getLinkedAccountByGroupAndUser(groupId, user.getUserId());

            if (account == null) {
                // Map.of()는 null 값을 허용하지 않으므로 HashMap 사용
                Map<String, Object> noAccount = new java.util.HashMap<>();
                noAccount.put("hasAccount", false);
                noAccount.put("account", null);
                return ResponseEntity.ok(noAccount);
            }

            // 은행명이 비어있으면 bankCode로 조회
            if (account.getBankName() == null || account.getBankName().isBlank()) {
                // AccountInfo에 bankCode 필드가 없으므로 DB에서 직접 bankName을 가져옴
                // (USER_ACCOUNTS.BANK_NAME 컬럼에 값이 있으면 XML에서 이미 채워짐)
            }

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("hasAccount", true);
            result.put("account", account);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.status(500).body("서버 오류: " + e.getMessage());
        }
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    public ResponseEntity<?> kickMember(
            @PathVariable("groupId") int groupId, 
            @PathVariable("userId") int userId) {
        
        try {
            // Service에 만든 removeMember 메서드를 호출합니다.
            boolean isRemoved = groupService.removeMember(groupId, userId);
            
            if (isRemoved) {
                // 성공 시 200 OK 반환
                return ResponseEntity.ok().body("멤버가 성공적으로 제외되었습니다.");
            } else {
                // 영향받은 행이 없을 경우 (이미 없거나 방장인 경우 등)
                return ResponseEntity.badRequest().body("멤버 제외에 실패했습니다. (대상 없음 또는 권한 부족)");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("서버 오류 발생: " + e.getMessage());
        }
    }
}