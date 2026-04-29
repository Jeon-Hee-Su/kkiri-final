package com.kkiri.security;

import java.io.IOException;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import org.springframework.beans.factory.annotation.Value;

import com.kkiri.model.vo.UserVO;
import com.kkiri.service.AuthService;
import com.kkiri.service.UserService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor 
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final AuthService authService;

    /** 요청이 HTTPS인지 자동 감지 */
    private boolean isSecureRequest(HttpServletRequest request) {
        String proto = request.getHeader("X-Forwarded-Proto");
        if (proto != null) return "https".equalsIgnoreCase(proto);
        return request.isSecure();
    }

    /** AuthApiController와 동일한 쿠키 발급 헬퍼 */
    private void addTokenCookie(HttpServletRequest request, HttpServletResponse response,
                                String name, String value, int maxAgeSeconds) {
        boolean secure = isSecureRequest(request);
        String cookie = name + "=" + value
                + "; Path=/"
                + "; Max-Age=" + maxAgeSeconds
                + "; HttpOnly"
                + (secure ? "; Secure; SameSite=None" : "; SameSite=Lax");
        response.addHeader("Set-Cookie", cookie);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        
        // 1. 소셜 로그인 정보 추출
        OAuth2AuthenticationToken authToken = (OAuth2AuthenticationToken) authentication;
        String provider = authToken.getAuthorizedClientRegistrationId(); 
        OAuth2User oAuth2User = authToken.getPrincipal();
        Map<String, Object> attributes = oAuth2User.getAttributes();
        
        String email = null;
        String name = null;
        String providerId = null; 
        String profileImage = null;

        // 제공자별 데이터 파싱
        if ("naver".equals(provider)) {
            Map<String, Object> naverResponse = (Map<String, Object>) attributes.get("response");
            email = (String) naverResponse.get("email");
            name = (String) naverResponse.get("name");
            providerId = (String) naverResponse.get("id"); 
            profileImage = (String) naverResponse.get("profile_image");
        } else if ("google".equals(provider)) {
            email = (String) attributes.get("email");
            name = (String) attributes.get("name");
            providerId = (String) attributes.get("sub"); 
            profileImage = (String) attributes.get("picture");
        }

        log.info("🎉 [{}] 소셜 인증 성공! 이메일: {}, 이미지: {}", provider.toUpperCase(), email, profileImage);

        // 2. 임시 세션 파괴 (보안상 권장)
        request.getSession().invalidate(); 
        Cookie jsessionCookie = new Cookie("JSESSIONID", null);
        jsessionCookie.setPath("/");
        jsessionCookie.setMaxAge(0);
        response.addCookie(jsessionCookie); 

        // 3. DB 조회 및 분기 처리
        UserVO existingUser = (email != null) ? userService.findByEmail(email) : null;

        if (existingUser != null) {
            // 🟢 [CASE A] 기존 회원 -> 토큰 생성

            String realToken = jwtUtil.generateAccessToken(
                    existingUser.getEmail(),
                    existingUser.getUserId(),
                    "Y".equals(existingUser.getIsVerified())	
            );
            
            String refreshToken = jwtUtil.generateRefreshToken(
                    existingUser.getEmail(),
                    existingUser.getUserId(),
                    "Y".equals(existingUser.getIsVerified())
            );
            authService.saveRefreshToken(existingUser.getUserId(), refreshToken);
            
            log.info("기존 회원 로그인 성공: {}", existingUser.getEmail());

            // 쿠키 발급
            addTokenCookie(request, response, "accessToken",  realToken,    1800);
            addTokenCookie(request, response, "refreshToken", refreshToken, 1209600);

            // pendingInviteCode 쿠키 확인 → 있으면 초대 수락 페이지로, 없으면 메인으로
            String pendingCode = null;
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("pendingInviteCode".equals(cookie.getName())) {
                        pendingCode = cookie.getValue();
                        break;
                    }
                }
            }

            // pendingInviteCode 쿠키 삭제
            Cookie clearCookie = new Cookie("pendingInviteCode", null);
            clearCookie.setPath("/");
            clearCookie.setMaxAge(0);
            response.addCookie(clearCookie);

            String redirectUrl = (pendingCode != null && !pendingCode.isEmpty())
                    ? "/join?code=" + pendingCode
                    : "/index";

            log.info("소셜 로그인 후 리다이렉트: {}", redirectUrl);
            getRedirectStrategy().sendRedirect(request, response, redirectUrl);
            
        } else {
            // 🟡 [CASE B] 신규 유저 -> 임시 토큰 발급 후 회원가입 이동
            log.info("신규 회원입니다. 회원가입 페이지로 이동합니다.");
            String tempToken = jwtUtil.generateTempToken(email != null ? email : "", name, provider, providerId); 
            
            String targetUrl = UriComponentsBuilder.fromUriString("/auth/signup")
                    .queryParam("token", tempToken)
                    .queryParam("hasEmail", (email != null && !email.isEmpty()))
                    .build().toUriString();
            
            getRedirectStrategy().sendRedirect(request, response, targetUrl);
        }
    } 
}