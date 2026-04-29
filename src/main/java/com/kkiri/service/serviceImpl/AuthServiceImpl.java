package com.kkiri.service.serviceImpl;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.kkiri.mapper.RefreshTokenMapper;
import com.kkiri.service.AuthService;
import com.kkiri.service.SseService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    
    private final RefreshTokenMapper refreshTokenMapper;
    private final RestTemplate restTemplate = new RestTemplate(); // API 호출용

    // application.properties에 등록해서 사용하는 것을 권장합니다.
    @Value("${portone.api.key:imp_apikey}") 
    private String apiKey;
    
    @Value("${portone.api.secret:imp_secret}")
    private String apiSecret;
    
    private final SseService sseService;
    /**
     * 1. 리프레시 토큰 저장
     */
    @Override
    @Transactional
    public void saveRefreshToken(int userId, String token) {
        LocalDateTime expiryDate = LocalDateTime.now().plusDays(14);
        try {
            // 1. 기존 토큰 있는지 확인 (다른 기기 감지용)
            String existingEmail = refreshTokenMapper.findEmailByUserId((long) userId);

            // 2. UPDATE 시도
            int updated = refreshTokenMapper.updateRefreshToken((long) userId, token, expiryDate);

            if (updated == 0) {
                // 3. UPDATE된 행 없음 = 첫 로그인 → INSERT
                refreshTokenMapper.saveRefreshToken((long) userId, token, expiryDate);
                log.info("Refresh Token 신규 저장 - 유저 ID: {}", userId);
            } else {
                // 4. UPDATE 성공 = 기존 기기 있었음 → SSE 푸시
                if (existingEmail != null) {
                    sseService.sendForceLogout(existingEmail);
                    log.info("다른 기기 로그인 감지 → SSE 강제 로그아웃 푸시: {}", existingEmail);
                }
                log.info("Refresh Token 갱신 - 유저 ID: {}", userId);
            }
        } catch (Exception e) {
            log.error("Refresh Token 저장 중 오류: {}", e.getMessage());
            throw new RuntimeException("토큰 저장 실패");
        }
    }

    /**
     * 2. 포트원 본인인증 정보 조회 (실제 구현부)
     */
    @Override
    public Map<String, String> getVerifiedInfo(String impUid) {
        try {
            // A. 포트원 Access Token 발급
            String accessToken = getPortOneToken();

            // B. 인증 정보 조회
            String url = "https://api.iamport.kr/certifications/" + impUid;
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> body = (Map<String, Object>) response.getBody().get("response");

            // C. 필요한 데이터 추출 및 가공
            Map<String, String> result = new HashMap<>();
            result.put("name", (String) body.get("name"));
            result.put("phone", (String) body.get("phone"));
            
            // 포트원 응답 객체에서 'unique_key' 또는 'ci'라는 키로 전달됩니다.
            String ci = (String) body.get("unique_key");
            if (ci == null) {
                ci = (String) body.get("ci");
            }
            result.put("ci", ci);
            
			/*
			 * // 포트원 응답에서 DI 값은 보통 'unique_in_site' 키로 들어옵니다. String di = (String)
			 * body.get("unique_in_site"); if (di == null) { di = (String) body.get("di");
			 * // 버전/환경에 따라 'di'일 수 있음 } result.put("di", di);
			 */

            // 생년월일 처리 (포트원은 보통 YYYY-MM-DD 또는 YYYYMMDD로 줌)
            String rawBirth = (String) body.get("birthday"); 
            if (rawBirth != null) {
                result.put("birth", rawBirth); // 950101 형식으로 변환
            }

            log.info("포트원 인증 정보 수신 성공: {}", result.get("name"));
            return result;

        } catch (Exception e) {
            log.error("포트원 API 연동 실패: {}", e.getMessage());
            throw new RuntimeException("본인인증 정보를 가져올 수 없습니다.");
        }
    }

    /**
     * 포트원 API 사용을 위한 내부 토큰 발급 로직
     */
    private String getPortOneToken() {
        String url = "https://api.iamport.kr/users/getToken";
        Map<String, String> request = new HashMap<>();
        request.put("imp_key", apiKey);
        request.put("imp_secret", apiSecret);

        Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
        Map<String, Object> responseDetail = (Map<String, Object>) response.get("response");
        return (String) responseDetail.get("access_token");
    }
    
    /**
     * 3. 리프레시 토큰 삭제 (로그아웃 시 사용)
     */
    @Override
    @Transactional
    public void deleteRefreshToken(int userId) {
        try {
            // 매퍼에 해당 메서드가 구현되어 있어야 합니다.
            refreshTokenMapper.deleteByUserId((long) userId);
            log.info("Refresh Token 삭제 완료 - 유저 ID: {}", userId);
        } catch (Exception e) {
            log.error("Refresh Token 삭제 중 오류: {}", e.getMessage());
            // 삭제 실패가 비즈니스 로직상 치명적이지 않다면 로그만 남겨도 되지만, 
            // 보안상 중요하므로 예외를 던지는 것이 안전합니다.
            throw new RuntimeException("토큰 삭제 실패");
        }
    }
    
    /**
     * 4. 리프레시 토큰 검증
     * DB에 저장된 토큰이 존재하는지, 만료되지 않았는지 확인합니다.
     */
    @Override
    @Transactional(readOnly = true)
    public boolean validateRefreshToken(String token) {
        try {
            // Mapper를 통해 DB에 해당 토큰이 있는지 확인
            // (Mapper에 findByTokenValue 같은 메서드가 있다고 가정)
            return refreshTokenMapper.existsByToken(token);
        } catch (Exception e) {
            log.error("토큰 검증 중 오류 발생: {}", e.getMessage());
            return false;
        }
    }
    
    // 리프레시 토큰 교체(중복로그인 확인용)
    @Override
    @Transactional
    public void rotateRefreshToken(int userId, String token) {
    	LocalDateTime expiryDate = LocalDateTime.now().plusDays(14);
        int updated = refreshTokenMapper.updateRefreshToken((long) userId, token, expiryDate);
        if (updated == 0) {
            refreshTokenMapper.saveRefreshToken((long) userId, token, expiryDate);
        }
        log.info("Refresh Token 교체 완료 (SSE 없음) - 유저 ID: {}", userId);
    }
}