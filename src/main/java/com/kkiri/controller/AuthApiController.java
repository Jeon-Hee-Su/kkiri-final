package com.kkiri.controller;

import java.util.Arrays;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.kkiri.common.EncryptionUtil;
import com.kkiri.model.dto.AuthDTO;
import com.kkiri.model.vo.SocialAccountVO;
import com.kkiri.model.vo.UserVO;
import com.kkiri.security.JwtUtil;
import com.kkiri.service.AuthService;
import com.kkiri.service.SseService;
import com.kkiri.service.UserService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private final UserService userService;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EncryptionUtil encryptionUtil;
    private final SseService sseService;

    // ───────────────────────────────────────────────
    // 쿠키 보안 설정
    /**
     * 요청이 HTTPS인지 자동 감지해서 쿠키 설정을 분기합니다.
     * - HTTPS (ngrok/운영): Secure; SameSite=None  → 모바일 포함 동작
     * - HTTP  (localhost):  SameSite=Lax            → 로컬 개발 동작
     *
     * application.properties에서 app.cookie.secure 설정 불필요!
     * server.forward-headers-strategy=native 설정이 되어 있어야 ngrok HTTPS를 인식합니다.
     */
    private boolean isSecureRequest(HttpServletRequest request) {
        // ngrok은 X-Forwarded-Proto 헤더로 HTTPS 여부를 전달
        String proto = request.getHeader("X-Forwarded-Proto");
        if (proto != null) return "https".equalsIgnoreCase(proto);
        return request.isSecure();
    }

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

    /** 쿠키 삭제용 */
    private void expireCookie(HttpServletRequest request, HttpServletResponse response, String name) {
        boolean secure = isSecureRequest(request);
        String cookie = name + "=;"
                + " Path=/;"
                + " Max-Age=0;"
                + " HttpOnly;"
                + (secure ? " Secure; SameSite=None" : " SameSite=Lax");
        response.addHeader("Set-Cookie", cookie);
    }

    // ───────────────────────────────────────────────
    // 1. 자체 회원가입
    // ───────────────────────────────────────────────
    @PostMapping("/signup")
    public ResponseEntity<String> register(@RequestBody AuthDTO.SignUpRequest signUpRequest) {
        log.info("자체 회원가입 요청: {}", signUpRequest.getEmail());

        if (userService.isCiExist(signUpRequest.getCi())) {
            return ResponseEntity.status(409).body("이미 가입된 회원입니다.");
        }
        
        if (userService.isPhoneExist(signUpRequest.getPhoneNumber())) {
            return ResponseEntity.status(409).body("이미 등록된 휴대폰 번호입니다.");
        }
        userService.registerUser(signUpRequest);
        return ResponseEntity.ok("회원가입이 완료되었습니다.");
    }

    // ───────────────────────────────────────────────
    // 2. 일반 로그인 (ID/PW)
    // ───────────────────────────────────────────────

    /**
     * [동시성 개선] @Transactional 적용
     * - DB에 refreshToken 저장이 성공한 뒤에만 쿠키를 세팅합니다.
     * - saveRefreshToken 실패 시 RuntimeException → 트랜잭션 롤백 → 쿠키 미발급.
     *   (쿠키를 먼저 addCookie하면 롤백해도 이미 전달된 쿠키를 회수할 수 없습니다.)
     *
     * [주의] HttpServletResponse는 트랜잭션 범위 밖 객체이므로
     * addCookie()는 트랜잭션 커밋 후 마지막에 호출합니다.
     */
    @PostMapping("/login")
    @Transactional
    public ResponseEntity<?> login(@RequestBody AuthDTO.LoginRequest loginRequest,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        log.info("로그인 시도: {}", loginRequest.getLoginId());

        UserVO user = userService.findByLoginId(loginRequest.getLoginId());

        if (user == null || !passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            return ResponseEntity.status(401).body("아이디 또는 비밀번호가 일치하지 않습니다.");
        }

        String userName = (user.getUserPrivacy() != null)
                ? user.getUserPrivacy().getName()
                : user.getNickname();

     // 1) Access Token 생성
        String accessToken = jwtUtil.generateAccessToken(
                user.getEmail(),
                user.getUserId(),
                "Y".equals(user.getIsVerified()) // String → boolean 변환
        );

        // 2) Refresh Token 생성 (uid 클레임 포함 버전 사용 권장)
        String refreshToken = jwtUtil.generateRefreshToken(
        		user.getEmail(), 
        		user.getUserId(),
        		"Y".equals(user.getIsVerified()));

        // 3) DB 저장 먼저 (실패 시 롤백, 쿠키 미발급)
        authService.saveRefreshToken(user.getUserId(), refreshToken);

        // 4) DB 저장 성공 후 쿠키 발급
        //    - accessToken: 30분 (accessTokenExpiration과 일치)
        //    - refreshToken: 14일 HttpOnly 쿠키 (Body 노출 없음)
        addTokenCookie(request, response, "accessToken",  accessToken,  60 * 30);
        addTokenCookie(request, response, "refreshToken", refreshToken, 60 * 60 * 24 * 14);

        log.info("로그인 성공: {}", user.getEmail());
        return ResponseEntity.ok(AuthDTO.AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .email(user.getEmail())
                .name(userName)
                .role("ROLE_USER")
                .profileImage(user.getProfileImage())
                .build());
    }

    // ───────────────────────────────────────────────
    // 3. 아이디 중복 확인
    // ───────────────────────────────────────────────
    @PostMapping("/check-id")
    public ResponseEntity<Boolean> checkId(@RequestBody Map<String, String> request) {
        String loginId = request.get("loginId");

        if (loginId == null || loginId.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        boolean isAvailable = userService.checkIdDuplicate(loginId);
        return ResponseEntity.ok(isAvailable);
    }

    // ───────────────────────────────────────────────
    // 4. 소셜 회원가입 완료 (임시 토큰 기반)
    // ───────────────────────────────────────────────

    /**
     * [보안 개선] tempToken 검증 강화
     * - role 클레임이 "ROLE_TEMP"인 토큰만 수락합니다.
     * - 일반 accessToken을 여기에 갖다 쓰는 것을 차단합니다.
     */
    @PostMapping("/complete-social-signup")
    @Transactional
    public ResponseEntity<?> completeSocialSignup(
            @RequestBody AuthDTO.SocialSignUpCompletionRequest signUpRequest) {

        String tempToken = signUpRequest.getTempToken();
        String email, provider, providerId;

        try {
            // 임시 토큰 타입 검증
            String role = jwtUtil.extractClaimByKey(tempToken, "role");
            if (!"ROLE_TEMP".equals(role)) {
                return ResponseEntity.status(400).body("유효하지 않은 가입 세션입니다.");
            }

            email      = jwtUtil.extractIdentifier(tempToken);
            provider   = jwtUtil.extractClaimByKey(tempToken, "provider");
            providerId = jwtUtil.extractClaimByKey(tempToken, "providerId");
        } catch (Exception e) {
            return ResponseEntity.status(400).body("유효하지 않은 가입 세션입니다.");
        }
        if (userService.isCiExist(signUpRequest.getCi())) {
            return ResponseEntity.status(409).body("이미 가입된 회원입니다.");
        }

        if (userService.isPhoneExist(signUpRequest.getPhoneNumber())) {
            return ResponseEntity.status(409).body("이미 등록된 휴대폰 번호입니다.");
        }

        UserVO newUser = userService.registerSocialUser(signUpRequest, email);

        SocialAccountVO socialAccount = new SocialAccountVO();
        socialAccount.setUserId(newUser.getUserId());
        socialAccount.setProvider(provider);
        socialAccount.setProviderId(providerId);
        userService.registerSocialAccount(socialAccount);

        // 소셜 회원가입 완료 후 토큰을 발급하지 않음
        // 로그인 페이지로 이동 후 직접 로그인해야 토큰이 발급됨
        log.info("소셜 회원가입 완료 (토큰 미발급): {}", newUser.getEmail());

        return ResponseEntity.ok(Map.of(
                "message", "회원가입이 완료되었습니다. 로그인해 주세요.",
                "email", newUser.getEmail()
        ));
    }

    // ───────────────────────────────────────────────
    // 5. 본인인증 정보 조회 (포트원)
    // ───────────────────────────────────────────────
    @PostMapping("/verify-cert")
    public ResponseEntity<?> verifyCert(@RequestBody Map<String, String> request) {
        String impUid = request.get("imp_uid");
        log.info("포트원 인증 요청 UID: {}", impUid);

        try {
            Map<String, String> certData = authService.getVerifiedInfo(impUid);

            // CI 추출 및 즉시 암호화
            String ci = certData.getOrDefault("ci", certData.get("unique_key"));
            if (ci != null) {
                certData.put("ci", encryptionUtil.encrypt(ci));
                log.info("CI 암호화 완료");
            } else {
                log.warn("CI 값을 가져오지 못했습니다.");
            }

            // 생년월일 가공 (YYYYMMDD → YYYY/MM/DD)
            String rawBirth = certData.get("birth");
            if (rawBirth != null) {
                String nums = rawBirth.replaceAll("[^0-9]", "");
                if (nums.length() == 8) {
                    certData.put("birth",
                            nums.substring(0, 4) + "/" + nums.substring(4, 6) + "/" + nums.substring(6, 8));
                }
            }

            // 전화번호 가공 (010-XXXX-XXXX)
            String rawPhone = certData.get("phone");
            if (rawPhone != null) {
                String nums = rawPhone.replace("-", "");
                if (nums.length() == 11) {
                    certData.put("phone", nums.replaceFirst("(^010)(\\d{4})(\\d{4})$", "$1-$2-$3"));
                } else if (nums.length() == 10) {
                    certData.put("phone", nums.replaceFirst("(^010)(\\d{3})(\\d{4})$", "$1-$2-$3"));
                }
            }

            log.info("인증 성공 - 이름: {}, 생일: {}", certData.get("name"), certData.get("birth"));
            return ResponseEntity.ok(certData);

        } catch (Exception e) {
            log.error("본인인증 처리 실패: ", e);
            return ResponseEntity.status(500).body("인증 정보를 처리하지 못했습니다.");
        }
    }

    // ───────────────────────────────────────────────
    // 6. Access Token 재발급 (Refresh Token 기반)
    // ───────────────────────────────────────────────

    /**
     * [구현 완료]
     *
     * 흐름:
     *  1. 요청 쿠키에서 refreshToken 추출
     *  2. JWT 서명·만료 검증
     *  3. type 클레임이 "refresh"인지 확인 (accessToken 혼용 차단)
     *  4. DB에서 STATUS='VALID' + EXPIRY_DATE > NOW() 검증
     *  5. 새 accessToken 발급 후 쿠키 갱신
     *
     * [Refresh Token Rotation 선택적 적용]
     * 보안 강화가 필요하면 아래 주석 처리된 로직을 활성화하세요.
     * - 재발급 시마다 refreshToken도 새로 발급하고 DB를 갱신합니다.
     * - 탈취된 토큰이 한 번 사용되면 즉시 무효화됩니다.
     */
    @PostMapping("/refresh")
    @Transactional
    public ResponseEntity<?> refreshToken(HttpServletRequest request, HttpServletResponse response) {

        // 1. 쿠키에서 refreshToken 추출 (Body 전달 대신 쿠키 사용)
        String refreshToken = null;
        if (request.getCookies() != null) {
            refreshToken = Arrays.stream(request.getCookies())
                    .filter(c -> "refreshToken".equals(c.getName()))
                    .findFirst()
                    .map(Cookie::getValue)
                    .orElse(null);
        }

        if (refreshToken == null) {
            return ResponseEntity.status(400).body("Refresh Token이 없습니다.");
        }

        // 2. JWT 서명·만료 검증
        if (!jwtUtil.isTokenValid(refreshToken)) {
            expireCookie(request, response, "refreshToken");
            return ResponseEntity.status(401).body("만료되었거나 유효하지 않은 Refresh Token입니다.");
        }

        // 3. 토큰 타입 검증 (accessToken을 여기에 갖다 쓰는 공격 차단)
        String tokenType = jwtUtil.extractClaimByKey(refreshToken, "type");
        if (!"refresh".equals(tokenType)) {
            return ResponseEntity.status(401).body("잘못된 토큰 타입입니다.");
        }

        // 4. DB 검증 (STATUS='VALID' + 미만료)
        if (!authService.validateRefreshToken(refreshToken)) {
            expireCookie(request, response, "refreshToken");
            return ResponseEntity.status(401).body("폐기되었거나 존재하지 않는 Refresh Token입니다.");
        }

        // 5. 클레임에서 사용자 정보 추출 (DB 조회 없이 처리)
        String email  = jwtUtil.extractIdentifier(refreshToken);
        String uidStr = jwtUtil.extractClaimByKey(refreshToken, "userid");

        if (email == null || uidStr == null) {
            return ResponseEntity.status(401).body("토큰에서 사용자 정보를 읽을 수 없습니다.");
        }

        int userId = Integer.parseInt(uidStr);

        // 6. 새 Access Token 발급
        //    isVerified는 DB를 안 거치기 위해 토큰 클레임에서 읽거나,
        //    보안상 중요하다면 DB에서 재조회합니다.
        String isVerifiedStr = jwtUtil.extractClaimByKey(refreshToken, "isVerified");
        boolean isVerified = "true".equals(isVerifiedStr); // null이면 자동으로 false

        String newAccessToken = jwtUtil.generateAccessToken(
        		email, 
        		userId, 
        		isVerified);

        addTokenCookie(request, response, "accessToken", newAccessToken, 60 * 30);

        // ── Refresh Token Rotation (중복 로그인 강제 로그아웃 핵심) ──
        // 새 기기에서 로그인하면 DB의 refreshToken이 교체됨
        // → 기존 기기가 /refresh 요청 시 DB 불일치 → 401 → 자동 로그아웃
        String newRefreshToken = jwtUtil.generateRefreshToken(email, userId, isVerified);
        authService.rotateRefreshToken(userId, newRefreshToken);
        addTokenCookie(request, response, "refreshToken", newRefreshToken, 60 * 60 * 24 * 14);
        // ────────────────────────────────────────────────────────────

        log.info("Access Token 재발급 성공: userId={}", userId);
        return ResponseEntity.ok(Map.of(
                "accessToken", newAccessToken,
                "tokenType", "Bearer"
        ));
    }

    // ───────────────────────────────────────────────
    // 7. 로그아웃
    // ───────────────────────────────────────────────

    /**
     * [권장] 로그아웃 엔드포인트
     * - DB의 refreshToken을 REVOKED 처리 (삭제 대신 상태 변경 → 감사 이력 보존)
     * - 클라이언트 쿠키 만료
     *
     * UserController에 이미 /api/user/logout이 있다면
     * 이 메서드 대신 그쪽에 DB 상태 변경 로직을 추가하세요.
     */
    @PostMapping("/logout")
    @Transactional
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {

        // 1. refreshToken 추출
        String refreshToken = null;
        if (request.getCookies() != null) {
            refreshToken = Arrays.stream(request.getCookies())
                    .filter(c -> "refreshToken".equals(c.getName()))
                    .findFirst()
                    .map(Cookie::getValue)
                    .orElse(null);
        }

        // 2. DB에서 삭제
        if (refreshToken != null && jwtUtil.isTokenValid(refreshToken)) {
            String uidStr = jwtUtil.extractClaimByKey(refreshToken, "userid");
            if (uidStr != null) {
                authService.deleteRefreshToken(Integer.parseInt(uidStr));
            }
        }

        // 3. 쿠키 만료
        expireCookie(request, response, "refreshToken");
        expireCookie(request, response, "accessToken");

        return ResponseEntity.ok("로그아웃 되었습니다.");
    }
    // ───────────────────────────────────────────────
    // 8. Access Token 남은 만료 시간 조회
    // ───────────────────────────────────────────────
 
    /**
     * 프론트엔드 헤더의 카운트다운 표시용
     * - accessToken 쿠키를 읽어 남은 시간(초)을 반환
     * - 만료 시 0 반환 (클라이언트가 refresh 트리거)
     */
    @PostMapping("/token-status")
    public ResponseEntity<?> getTokenStatus(HttpServletRequest request) {
        String accessToken = null;
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("accessToken".equals(c.getName())) {
                    accessToken = c.getValue();
                    break;
                }
            }
        }
 
        if (accessToken == null) {
            return ResponseEntity.ok(Map.of("remainingSeconds", 0, "valid", false));
        }
 
        long remaining = jwtUtil.getTokenRemainingSeconds(accessToken);
        return ResponseEntity.ok(Map.of(
                "remainingSeconds", remaining,
                "valid", remaining > 0
        ));
    }
    @GetMapping(value = "/session-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sessionStream(Authentication authentication) {
        if (authentication == null) return null;
        return sseService.register(authentication.getName());
    }
}