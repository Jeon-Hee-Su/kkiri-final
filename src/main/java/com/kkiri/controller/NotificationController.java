package com.kkiri.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.kkiri.model.vo.NotificationVO;
import com.kkiri.security.JwtUtil;
import com.kkiri.service.NotificationService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private JwtUtil jwtUtil;

    /**
     * [1] SSE 구독 연결 (JS: new EventSource)
     * 브라우저와 서버 사이의 실시간 통로를 개설합니다.
     */
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestParam int userId) {
        // [수정됨] 서비스의 subscribe를 호출하여 Emitter 객체를 반환합니다.
        return notificationService.subscribe(userId);
    }

    /**
     * [2] 알림 목록 조회 (JS: loadNotification)
     * 종 아이콘 클릭 시 DB에 저장된 최근 알림 20개를 가져옵니다.
     */
    @GetMapping("/list")
    public List<NotificationVO> getList(@CookieValue(name = "accessToken", required = false) String token) {
        // [수정됨] 쿠키의 토큰에서 userId를 추출합니다.
        int userId = jwtUtil.extractUserId(token);
        System.out.println("🚀 [알림조회] 토큰에서 추출한 유저 ID: " + userId);
        
     // 💡 [중요] 여기서 찍히는 숫자가 DB에 형님이 넣은 USER_ID랑 똑같은지 무조건 확인!
        System.out.println("┌──────────────────────────────────────────┐");
        System.out.println("│ 🚀 알림 조회 요청 유저 ID: " + userId);
        
        // 2. 서비스 호출
        List<NotificationVO> list = notificationService.getNotificationList(userId);
        
        // 💡 [중요] DB에서 몇 건이나 가져왔는지 확인!
        System.out.println("│ 🚀 조회된 알림 개수: " + (list != null ? list.size() : 0));
        System.out.println("└──────────────────────────────────────────┘");
        
        return list;
    }

    /**
     * [3] 개별 알림 읽음 처리 (JS: markAsRead)
     * 특정 알림을 클릭했을 때 IS_READ를 'Y'로 바꿉니다.
     */
    @PostMapping("/read/{notiId}")
    public ResponseEntity<String> readNotification(@PathVariable int notiId) {
        // [수정됨] 서비스의 markAsRead를 호출하며 알림 번호를 넘깁니다.
        notificationService.markAsRead(notiId);
        return ResponseEntity.ok("success");
    }

    /**
     * [4] 전체 알림 읽음 처리 (JS: readall)
     * '모두 읽음' 버튼을 눌렀을 때 해당 유저의 모든 알림을 업데이트합니다.
     */
    @PostMapping("/read-all")
    public ResponseEntity<String> readAllNotifications(
    		@CookieValue(name = "accessToken", required = false) String token) {
        int userId = jwtUtil.extractUserId(token);
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok("success");
    }
    
    @GetMapping("/read-status")
    public ResponseEntity<Map<String, Boolean>> checkUnreadStatus(HttpServletRequest request, 
    			@CookieValue(name = "accessToken", required = false) String token) {
        // [수정됨] HttpOnly 쿠키에 담긴 토큰은 Spring Security나 인터셉터가 자동으로 검증합니다.
        // 세션이나 SecurityContext에서 현재 로그인한 유저 ID를 가져옵니다.
        int userId = jwtUtil.extractUserId(token); 
        
        // DB에서 is_read = 'N' 인 알림이 하나라도 있는지 확인 (EXISTS 쿼리 권장)
        boolean hasUnread = notificationService.hasUnreadNoti(userId);
        
        Map<String, Boolean> response = new HashMap<>();
        response.put("hasUnread", hasUnread);
        
        return ResponseEntity.ok(response);
    }

}