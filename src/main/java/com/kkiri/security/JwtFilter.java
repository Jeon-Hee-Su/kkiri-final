package com.kkiri.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String path = request.getRequestURI();
        log.info("Checking Request Path: {}", path);
        
        // 1. 토큰 검사 없이 통과시킬 경로 (SecurityConfig 설정과 맞춤)
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. 헤더에서 'accessToken'을 직접 꺼냅니다. (스크린샷의 로컬 스토리지 Key와 일치)
        // 프론트엔드에서 headers: { "accessToken": "..." } 로 보내야 합니다.
        String accessToken = resolveToken(request);
        String email = null;

        // 3. accessToken이 존재할 경우 이메일 추출 시도
        if (accessToken != null && !accessToken.isEmpty()) {
            try {
                email = jwtUtil.extractIdentifier(accessToken);
            } catch (Exception e) {
                log.error("AccessToken에서 이메일 추출 중 오류 발생: {}", e.getMessage());
                filterChain.doFilter(request, response);
                return; // 예외 발생 시 이후 로직 실행 방지
            }
        }

        // 4. 이메일이 정상적으로 추출되었고, 아직 인증되지 않은 요청인 경우
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            
            // 토큰 유효성 및 만료 여부 확인
            if (jwtUtil.isTokenValid(accessToken)) {
                
                // 토큰에서 사용자 권한(Role) 추출
                //String role = jwtUtil.extractRole(accessToken);

                // 5. 스프링 시큐리티 인증 객체 생성
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        email, 
                        null, 
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                );

                // 6. SecurityContext에 인증 정보 저장 (로그인 상태 유지)
                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.info("인증 성공: 사용자 {} (Role: {})", email);
            }
        }

        // 7. 다음 필터 또는 컨트롤러로 요청 전달
        filterChain.doFilter(request, response);
    }

    // 헤더뿐만 아니라 쿠키에서도 토큰을 찾도록 로직 추가/수정
    private String resolveToken(HttpServletRequest request) {
        // 1. 기존 방식 (API 요청용: Authorization 헤더 검사)
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        // 2. 🚨 [추가] 헤더에 없으면 쿠키(Cookie)에서 'accessToken'을 찾음 (화면 이동용)
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("accessToken".equals(cookie.getName())) {
                	log.info("🎯 쿠키에서 토큰 발견: {}", cookie.getValue().substring(0, 10) + "...");
                    return cookie.getValue();
                }
            }
        }
        log.warn("❌ 요청에 accessToken 쿠키가 없습니다!");
        return null; // 못 찾으면 null 반환 (알아서 시큐리티가 막아줌)
    }
    
    /**
     * 보안 필터를 거치지 않아도 되는 공개 경로 리스트
     */
    private boolean isPublicPath(String path) {
        return path.equals("/") ||
                path.startsWith("/index") ||
                path.startsWith("/auth/") ||
                (path.startsWith("/api/auth/") && !path.equals("/api/auth/session-stream")) || // ← 수정
                path.startsWith("/oauth2/") ||
                path.startsWith("/join") ||
                path.startsWith("/css/") || 
                path.startsWith("/js/") || 
                path.startsWith("/img/") ||
                path.equals("/favicon.ico") ||
                path.equals("/error") ||
                path.equals("/posscanner") ||
                path.equals("/api/payment/qr/pay") || 
                path.startsWith("/api/fcm/") ||
                path.equals("/firebase-messaging-sw.js") ||
                path.startsWith("/api/admin/auto-transfer/");
    }
}