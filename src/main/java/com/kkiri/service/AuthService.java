package com.kkiri.service;

import java.util.Map;

public interface AuthService {

	void saveRefreshToken(int userId, String token);
	
	// 2. 포트원으로부터 실제 본인인증 데이터 가져오기 (신규)
    // impUid를 받아 이름, 전화번호, 생년월일 등이 담긴 Map을 반환
    Map<String, String> getVerifiedInfo(String impUid);

    // 3. 리프레시 토큰 삭제 (로그아웃 시 사용)
    void deleteRefreshToken(int userId);
    
    // 4. 리프레시 토큰 검증 (토큰 재발급 시 사용 예정)
    boolean validateRefreshToken(String token);
    // 5. 리프레시 토큰 교체 (중복 로그인 확인용)
    void rotateRefreshToken(int userId, String token);
}
