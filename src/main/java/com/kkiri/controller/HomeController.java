package com.kkiri.controller;

import java.io.File;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.kkiri.mapper.BankMapper;
import com.kkiri.model.dto.GroupDTO;
import com.kkiri.model.dto.GroupDTO.GroupDetailResponse;
import com.kkiri.model.vo.GroupAccountVO;
import com.kkiri.model.vo.GroupVO;
import com.kkiri.model.vo.UserVO;
import com.kkiri.service.GroupService;
import com.kkiri.service.OcrService;
import com.kkiri.service.UserService;

@Controller
public class HomeController {
   
   @Autowired
    private GroupService groupService;
   
   @Autowired
   private UserService userService;
   
   @Autowired
   private BankMapper bankMapper;
   
   @GetMapping("/")
   public String home() {
       return "index";
   }
   
   @GetMapping("/index")
   public String index() {
       return "index";  // templates/index.html
   }
   
   @GetMapping("/account")
   public String account() {
       return "auth/account";
   }
   
   
   
   @GetMapping("/changePW")
   public String changePW() {
      return "auth/changePW-modal";
   }
   

   
   @GetMapping("/service_center")
   public String service_center() {
      return "support/service_center";
   }
   
   // /groupmain 제거 - /group/detail/{groupId} 로 통일됨
   
   @GetMapping("/creategroup")
   public String creategroup() {
      return "group/create-group";
   }
   
   @GetMapping("/invite")
    public String invite(@RequestParam(value="groupId", required = false) Integer groupId, Model model) {
        if (groupId == null) {
            // 주소창에 ?groupId=번호 가 없으면 null을 보냄
            model.addAttribute("group", null); 
        } else {
            // DB에서 해당 ID의 그룹 정보를 가져와서 "group"이라는 이름으로 보따리에 담음
            GroupVO group = groupService.getGroupById(groupId); 
            model.addAttribute("group", group);
        }
        return "group/invite";
    }
   @GetMapping("/invitecard")
   public String invitecard() {
      return "group/invite-card";
   }
   
   @GetMapping("/join")
   public String joinByInviteCode(@RequestParam("code") String code, Model model) {
       model.addAttribute("inviteCode", code);
       return "group/invite-card"; // invite-card.html이 초대장 수락 페이지
   }

   @GetMapping("/analysis")
   public String getAnalysisPage() {
       return "analysis/analysis"; 
   }
   @GetMapping("/receipts")
    public String receiptPage(
            @RequestParam(value = "transactionId", required = false) Long transactionId,
            @RequestParam(value = "groupId",       required = false) Integer groupId,
            Model model) {
        model.addAttribute("transactionId", transactionId != null ? transactionId : 0L);
        model.addAttribute("linkedGroupId",  groupId      != null ? groupId      : 0);
        return "payment/receipts";
    }
   @GetMapping("/passbook") // 브라우저 주소창에 입력될 주소
   public String passbook() {
       return "payment/create-passbook"; 
   }
   @GetMapping("/host-link-account")
   public String hostLinkAccount(@RequestParam("groupId") Integer groupId, Model model) {
       model.addAttribute("groupId", groupId);
       return "payment/host-link-account";
   }

   @GetMapping("/passbookfinish")
   public String passbookfinish(@RequestParam(value="groupId", required = false) Integer groupId, Model model) {
       model.addAttribute("groupId", groupId);

       if (groupId != null) {
           GroupVO group = groupService.getGroupById(groupId);
           GroupAccountVO account = groupService.getGroupAccountByGroupId(groupId);

           if (group != null) {
               model.addAttribute("groupName", group.getGroupName());
           }
           if (account != null) {
               model.addAttribute("account", account);
               // ✅ 추가
               String bankName = bankMapper.findBankName(account.getBankCode());
               model.addAttribute("bankName", bankName);
           }
       }
       return "payment/create-passbook-finish";
   }
   
   @GetMapping("/paymentmethod")
   public String paymentmethod() {
       // 🚩 서버에서 세션 체크를 하지 않고 일단 HTML을 내려줍니다.
       return "payment/paymentmethod";
   }
   
   @GetMapping("/setting")
   public String setting() {
      return "support/setting";
   }
   
   @GetMapping("/groupmembers")
   public String groupMembers(@RequestParam(value="groupId", defaultValue="1") int groupId, Model model) {
      // [임시 데이터] DB 연동 시 해당 그룹의 정보를 조회하여 대체해 주세요.
      // 필요한 정보: group.inviteCode, 멤버 리스트(JS에서 처리 중)
      Map<String, Object> group = Map.of(
              "groupId", groupId, // 이제 주소창의 숫자가 여기로 들어옵니다!
              "inviteCode", "KRI-" + (int)(Math.random() * 9000 + 1000) // 임시 랜덤 코드
          );
          
          model.addAttribute("group", group);

          return "group/group-members"; 
   }
   
   @GetMapping({"/history", "/group/history"})
   public String history(@RequestParam(value = "groupId", required = false) Long groupId, Model model) {
       if (groupId == null) {
           // groupId가 없으면 에러를 내는 대신 메인으로 보내거나 처리를 해줌
           return "redirect:/group-main"; 
       }
       model.addAttribute("groupId", groupId);
       return "group/group-history"; 
   }
// HomeController.java (일반 @Controller 클래스 내부)


   @GetMapping("/group/{groupId}/history")
   public String groupHistory(@PathVariable Long groupId, Model model) {
       model.addAttribute("groupId", groupId);
       return "group/group-history";
   }
   
   
//   @GetMapping("/groupsettings")
//   public String groupSettings(Model model) {
//      // 1. 그룹 기본 정보 (GROUPS 테이블 기반)
//      // 실제 로직에선 세션의 groupId 등으로 DB 조회
//      Map<String, Object> group = Map.of(
//         "groupName", "우리팀 회식 모임",
//         "inviteCode", "KIRI-7788-XY"
//      );
//      model.addAttribute("group", group);
//
//      // 2. 계좌 정보 (USER_ACCOUNTS + BANKS 테이블 기반)
//      Map<String, Object> bank = Map.of(
//         "bankName", "신한은행"
//      );
//      Map<String, Object> account = Map.of(
//         "accountNumber", "110-***-567890",
//         "accountOwner", "홍길동"
//      );
//      model.addAttribute("bank", bank);
//      model.addAttribute("account", account);
//
//      // 3. 권한 및 상태값
//      model.addAttribute("isMaster", true); // 마스터 여부에 따라 삭제 버튼 노출 제어 가능
//
//      return "group/group-settings";
//   }
	/*
	 * @GetMapping("/groupsettings") public String groupSettings(Model model,
	 * Authentication authentication) { // 1. 유저 ID 결정 (인증 없으면 테스트용 159번) int userId
	 * = 159; if (authentication != null && authentication.isAuthenticated()) {
	 * UserVO user = userService.findByEmail(authentication.getName()); if (user !=
	 * null) userId = user.getUserId(); }
	 * 
	 * try { // 2. 그룹 정보 조회 List<GroupVO> groups =
	 * groupService.findGroupsByUserId(userId);
	 * 
	 * if (groups != null && !groups.isEmpty()) { GroupVO myGroup = groups.get(0);
	 * model.addAttribute("group", myGroup); model.addAttribute("isMaster", true);
	 * 
	 * // 3. 주계좌 정보 조회 (AccountVO 타입 사용) UserAccountVO primaryAccount =
	 * userService.findPrimaryAccountByUserId(userId);
	 * 
	 * if (primaryAccount != null) { model.addAttribute("bank", Map.of("bankName",
	 * primaryAccount.getBankCode())); model.addAttribute("account", Map.of(
	 * "accountNumber", primaryAccount.getAccountNumber(), "accountOwner",
	 * primaryAccount.getUserId() )); } } else { model.addAttribute("group",
	 * Map.of("groupName", "소속 그룹 없음", "inviteCode", "0000"));
	 * model.addAttribute("isMaster", false); } } catch (Exception e) {
	 * System.out.println("❌ 데이터 조회 에러: " + e.getMessage()); }
	 * 
	 * return "group/group-settings"; }
	 */
// 기존 @GetMapping("/group/settings/{groupId}") 부분을 아래로 교체
// 1. 주소 매핑을 /group/settings 에서 /groupsettings로 단순화 (경로 충돌 방지)
   @GetMapping("/groupsettings") 
   public String groupSettings(@RequestParam("groupId") int groupId, Model model) {
       
       try {
           // 2. 데이터 가져오기 (권한 체크 생략 - 일단 화면부터 띄우기 위해)
           GroupDTO.GroupDetailResponse group = groupService.getGroupDetail(groupId);
           List<UserVO> memberList = groupService.findMembersByGroupId(groupId);

           if (group == null) return "redirect:/index";

           // 3. 모델에 데이터 담기
           model.addAttribute("group", group);       
           model.addAttribute("memberList", memberList);
           model.addAttribute("groupId", groupId);
           model.addAttribute("isMaster", true); 

       } catch (Exception e) {
           e.printStackTrace();
           return "redirect:/index"; 
       }

       // 4. 리턴값은 기존과 동일 (templates/group/group-settings.html)
       return "group/group-settings";
   }
   @PostMapping("/api/group/update-info") // 주소를 JS 요청과 일치시킴!
   @ResponseBody
   public Map<String, Object> updateGroupInfo(@RequestBody Map<String, Object> params) {
       Map<String, Object> result = new HashMap<>();
       
       try {
           // 필수 파라미터 체크 (groupId, groupName, category)
           if (params.get("groupId") != null && params.get("groupName") != null && params.get("category") != null) {
               
               int groupId = Integer.parseInt(params.get("groupId").toString());
               String newName = params.get("groupName").toString();
               String newCategory = params.get("category").toString(); // 카테고리 추가!
               
               // Service 호출 (이름과 카테고리를 같이 업데이트하도록 서비스 메서드 수정 필요)
               boolean success = groupService.updateGroupInfo(groupId, newName, newCategory);
               
               result.put("success", success);
           } else {
               result.put("success", false);
               result.put("message", "필수 데이터(ID, 이름, 목적)가 누락되었습니다.");
           }
       } catch (Exception e) {
           result.put("success", false);
           result.put("error", e.getMessage());
           e.printStackTrace(); // 서버 콘솔에서 에러 확인용
       }
       
       return result;
   }
   //invite-card
   @GetMapping("/link-account")
   public String linkAccountPage(@RequestParam("code") String code, Model model) {
       model.addAttribute("inviteCode", code);
       return "group/link-account"; // templates/link-account.html 파일 호출
   }
   
   
   @GetMapping("/api/groups/by-code/{code}")
   @ResponseBody
   public ResponseEntity<?> getGroupByCode(@PathVariable("code") String code) {
       try {
           // [수정] VO 대신 Map으로 받으면 닉네임, 멤버수 등 동적 데이터를 담기 편합니다.
           Map<String, Object> groupInfo = groupService.getGroupDetailsByCode(code);
           
           if (groupInfo != null) {
               // 이미 쿼리에서 "hostName", "memberCount" 등을 다 가져왔으므로 그대로 반환
               return ResponseEntity.ok(groupInfo);
           } else {
               return ResponseEntity.status(404).body("그룹을 찾을 수 없습니다.");
           }
       } catch (Exception e) {
           e.printStackTrace();
           return ResponseEntity.status(500).body("서버 오류 발생");
       }
   }
   
   @Autowired
   private OcrService ocrService;

   @PostMapping("/api/ocr/analyze")
   @ResponseBody
   public ResponseEntity<?> analyzeReceipt(@RequestParam("file") MultipartFile file) {
       try {
           // 1. 임시 디렉토리에 파일 저장 (경로 설정)
           String uploadDir = System.getProperty("user.dir") + "\\src\\main\\resources\\static\\uploads\\temp\\";
           File dir = new File(uploadDir);
           if (!dir.exists()) dir.mkdirs();

           // 2. 파일명 중복 방지를 위한 타임스탬프 적용
           String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
           File targetFile = new File(uploadDir + fileName);
           file.transferTo(targetFile);

           // 3. OCR 서비스 실행 (파이썬 스크립트 호출)
           String jsonResult = ocrService.executeOcr(targetFile.getAbsolutePath());

           // 4. [중요] 분석 데이터와 파일명을 함께 Map에 담아 리턴
           // 그래야 JS가 "이 파일명을 나중에 저장할 때 써야지"라고 기억할 수 있습니다.
           return ResponseEntity.ok(Map.of(
               "ocrData", jsonResult,
               "fileName", fileName
           ));
           
       } catch (Exception e) {
           e.printStackTrace();
           return ResponseEntity.status(500).body("OCR 분석 실패: " + e.getMessage());
       }
   }
   
   // 그룹 메인 페이지 - /group/detail/{groupId} 로 통일
    @GetMapping("/group/detail/{groupId}")
    public String groupDetail(@PathVariable("groupId") int groupId,
                              Model model,
                              Authentication authentication) {

        // groupId가 0이면 로그인 유저의 첫 번째 그룹으로 자동 결정
        if (groupId == 0 && authentication != null) {
            try {
                UserVO user = userService.findByEmail(authentication.getName());
                if (user != null) {
                    List<com.kkiri.model.vo.GroupVO> groups = groupService.findGroupsByUserId(user.getUserId());
                    if (groups != null && !groups.isEmpty()) {
                        return "redirect:/group/detail/" + groups.get(0).getGroupId();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return "redirect:/"; // 그룹이 없으면 인덱스로
        }

        GroupDetailResponse group = groupService.getGroupDetail(groupId);
        if (group == null) {
            return "redirect:/";
        }

        // isMaster 판별
        boolean isMaster = false;
        if (authentication != null) {
            try {
                UserVO user = userService.findByEmail(authentication.getName());
                if (user != null) {
                    String role = groupService.getUserRoleInGroup(groupId, user.getUserId());
                    isMaster = "HOST".equals(role);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 그룹 계좌 표시명 조합 (은행명 + 계좌번호)
        String groupAccountName = "";
        if (group.getBankName() != null && group.getAccountNumber() != null) {
            groupAccountName = group.getBankName() + " (" + group.getAccountNumber() + ")";
        } else if (group.getAccountNumber() != null) {
            groupAccountName = group.getAccountNumber();
        }

        model.addAttribute("groupId", groupId);
        model.addAttribute("group", group);
        model.addAttribute("currentGroupName", group.getGroupName());
        model.addAttribute("groupAccountName", groupAccountName);
        model.addAttribute("groupBalance", group.getBalance());
        model.addAttribute("isMaster", isMaster);

        return "group/group-main";
    }

    // --- [추가] 2. index.js의 fetchMyGroups()가 호출하는 API ---
    @GetMapping("/api/groups/my-list")
    @ResponseBody
    public ResponseEntity<List<GroupVO>> getMyGroupList(Authentication authentication) {
        try {
            if (authentication == null) return ResponseEntity.status(401).build();
            UserVO user = userService.findByEmail(authentication.getName());
            if (user == null) return ResponseEntity.status(404).build();
            List<GroupVO> groups = groupService.findGroupsByUserId(user.getUserId());
            return ResponseEntity.ok(groups);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }
    
    
    @GetMapping("/charge")
   public String charge() {
       return "payment/charge";
   }
   
    @GetMapping("/posscanner")
    public String posscanner() {
    	return "scanner/pos-scanner";
    }
    @GetMapping("/group/{groupId}/calculation")
    public String goGroupCalculation(@PathVariable("groupId") int groupId, 
                                     @RequestParam(value = "startDate", required = false) String startDate,
                                     @RequestParam(value = "endDate", required = false) String endDate,
                                     Principal principal, 
                                     Model model) {
        try {
            UserVO loginUser = userService.findByEmail(principal.getName());
            
            // 1. 권한 체크
            boolean isHost = groupService.isHost(groupId, String.valueOf(loginUser.getUserId())); 
            if (!isHost) {
                return "redirect:/group/" + groupId + "?error=not_host";
            }

            // 2. 데이터 가져오기
            GroupDTO.GroupDetailResponse group = groupService.getGroupDetail(groupId);
            List<UserVO> members = groupService.findMembersByGroupId(groupId);
            List<Map<String, Object>> expenses = groupService.getExpensesList((long)groupId, startDate, endDate);

            // 3. 데이터 전달 (이름을 memberList로 통일!)
            model.addAttribute("group", group);
            model.addAttribute("expenses", expenses);
            model.addAttribute("memberList", members); // HTML의 th:each="member : ${memberList}"와 매칭
            model.addAttribute("groupId", groupId);
            model.addAttribute("isMaster", isHost); // 실제 호스트 여부 전달

        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/index";
        }
        return "group/group-calculation";
    }

    // 정산 관리 페이지 뷰
    @GetMapping("/group/{groupId}/settlement")
    public String settlementPage(@PathVariable int groupId, Model model) {
        model.addAttribute("groupId", groupId);
        return "group/settlement";
    }
    @ResponseBody
    @GetMapping("/api/group/{groupId}/expenses")
    public List<Map<String, Object>> getExpensesApi(
        @PathVariable Long groupId,
        @RequestParam String startDate,
        @RequestParam String endDate) {
        
      
        return groupService.getExpensesList(groupId, startDate, endDate);
    }
 // Controller 예시
 // Controller 예시 (이 코드가 있는지 확인하세요)
    @PostMapping("/api/settlement/alarm") 
    public ResponseEntity<?> sendAlarm(@RequestBody Map<String, Object> data) {
        // 1. 데이터 꺼내기
        List<Integer> memberIds = (List<Integer>) data.get("memberIds");
        int groupId = Integer.parseInt(data.get("groupId").toString());
        String message = (String) data.get("message");

        // 2. ⭐ 이 줄이 없으면 DB에 절대 안 들어갑니다!
        groupService.saveNotifications(memberIds, groupId, message); 

        return ResponseEntity.ok().build();
    }
}