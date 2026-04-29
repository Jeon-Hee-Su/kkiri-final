package com.kkiri.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kkiri.model.dto.AuthDTO;
import com.kkiri.model.vo.UserPrivacyVO;
import com.kkiri.model.vo.UserVO;
import com.kkiri.security.JwtUtil;
import com.kkiri.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // 1. 개인 프로필 내용 조회	
    @GetMapping("/profile/details")
    public ResponseEntity<?> getUserProfileDetails(Authentication authentication) {
        // 1. 시큐리티 필터(JwtFilter)를 통과하지 못해 인증 정보가 없다면 401 에러 반환
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body("로그인이 필요합니다.");
        }
        
        // 2. JwtFilter에서 우리는 email을 Principal로 넣었습니다.
        String email = authentication.getName(); 
        UserVO user = userService.findByEmail(email); // 이메일로 유저 정보 조회
        
        if (user != null) {
            Map<String, Object> userData = new HashMap<>();
            
            // 1. UserVO (기본 정보)
            userData.put("userId", user.getUserId());
            userData.put("nickname", user.getNickname());
            userData.put("profileImage", user.getProfileImage());
            
            // 2. UserPrivacyVO (개인 정보) 처리
            UserPrivacyVO privacy = user.getUserPrivacy(); // UserVO에 포함된 객체
            if (privacy != null) {
                userData.put("name", privacy.getName());
                userData.put("phone", privacy.getPhoneNumber()); // 프론트엔드와 맞추기 위해 "phone" 키 사용
                userData.put("birth", privacy.getBirthDate());   // 프론트엔드와 맞추기 위해 "birth" 키 사용
                // 만약 이메일 정보가 Privacy에 없다면 가입 이메일 사용
                userData.put("email", email); 	
            } else {
                // 정보가 전혀 없을 경우 기본값
                userData.put("name", user.getNickname());
                userData.put("email", email);
                userData.put("phone", "연락처 미등록");
                userData.put("birth", "생년월일 미등록");
            }
            
            return ResponseEntity.ok(userData);
        }
        
        return ResponseEntity.status(404).body("사용자를 찾을 수 없습니다.");
    }
    
    // 2. 프로필 업데이트
    @PostMapping("/profile/update")
    public ResponseEntity<?> updateProfile(@RequestBody AuthDTO.UpdateProfileRequest updateRequest,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body("로그인이 필요합니다.");
        }
        String email = authentication.getName();
        
        UserVO user = userService.findByEmail(email);
        
        if(user == null) {
            return ResponseEntity.status(404).body("사용자를 찾을 수 없습니다.");
        }
        
        // 서비스 레이어 호출 (내부적으로 USERS와 USER_PRIVACY 양쪽을 UPDATE 하도록 구현되어 있어야 합니다)
        userService.updateUserInfo(user.getUserId(), updateRequest);
        
        return ResponseEntity.ok("정보가 성공적으로 수정되었습니다.");
    }
    
    // 3. 비밀번호 변경
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody AuthDTO.ChangePWRequest changeRe) {

        // 새 비밀번호 암호화 후 업데이트
        String encodePW = passwordEncoder.encode(changeRe.getNewpw());
        boolean isUpdated = userService.updatePW(changeRe.getName(), changeRe.getBirth(), encodePW);
        
        if(!isUpdated) {
        	return ResponseEntity.status(404).body(Map.of("message", "일치하는 사용자가 없습니다."));
        }
        return ResponseEntity.ok(Map.of("message", "비밀번호가 변경되었습니다."));
    }

    // JS에서 현재 로그인 유저 ID 조회용 (HttpOnly 쿠키 우회)
    @GetMapping("/me")
    public ResponseEntity<?> getMe(Authentication authentication) {
        if (authentication == null) return ResponseEntity.status(401).build();
        UserVO user = userService.findByEmail(authentication.getName());
        if (user == null) return ResponseEntity.status(404).build();
        return ResponseEntity.ok(Map.of("userId", user.getUserId()));
    }
}