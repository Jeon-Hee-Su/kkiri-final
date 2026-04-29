package com.kkiri.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kkiri.security.JwtUtil;
import com.kkiri.service.FcmService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/fcm")
public class FcmController {
	
	@Autowired
	private FcmService fcmService;
	
	@Autowired
	private JwtUtil jwtUtil;
	
	@PostMapping("/save-token")
	public ResponseEntity<String> saveToken(
			HttpServletRequest servletRequest,
			@RequestBody Map<String, Object> request
			) {
		
		try {
			String token = null;
			Cookie[] cookies = servletRequest.getCookies();
			
			if(cookies != null) {
				for(Cookie cookie : cookies) {
					if("accessToken".equals(cookie.getName())) {
						token = cookie.getValue();
						break;
					}
				}
			}
			
			if(token == null) {
				log.error("쿠키에 토큰 찾기 실패");
				return ResponseEntity.status(401).body("token_not_found");
			}
			
			int userId = jwtUtil.extractUserId(token);
	        String uuid = String.valueOf(request.get("uuid"));
	        String fcmToken = String.valueOf(request.get("fcmToken"));
	        
	        if(userId == 0) {
	        	log.error("userId 가 0임");
	        	return ResponseEntity.status(401).body("invalid_user");
	        }
	        
	        // 💡 프론트에서 넘어온 데이터가 있는지부터 확인
	        if (request.get("fcmToken") == null || request.get("uuid") == null) {
	            log.error("❌ 필수 데이터 누락상세: fcmToken: {} uuid: {}", 
	            		request.get("fcmToken"), request.get("uuid"));
	            return ResponseEntity.badRequest().body("data_missing");
	        }

	        log.info("💾 FCM 토큰 저장 - UserID: {}, Token: {}, UUID: {}", userId, fcmToken, uuid);

	        fcmService.saveToken(userId, fcmToken, uuid);
	        return ResponseEntity.ok("success");

	    } catch (Exception e) {
	        log.error("FCM 저장 중 서버 에러: ", e);
	        return ResponseEntity.internalServerError().body("server_error");
	    }	
	}
	
	// 알림 껐을때 토큰 삭제
	@PostMapping("/delete-token")
	public ResponseEntity<String> deleteToken(@RequestBody Map<String, String> request) {
		String fcmToken = request.get("fcmToken");
		
		if(fcmToken != null && !fcmToken.isEmpty()) {
			
			fcmService.deleteToken(fcmToken);
			
			return ResponseEntity.ok("deleted");
		}
		return ResponseEntity.badRequest().body("token_is_empty");
	}
}








